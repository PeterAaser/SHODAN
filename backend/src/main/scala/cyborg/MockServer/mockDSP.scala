package cyborg

import cats.data.Chain
import cats.effect._
import cats._
import cats.effect.concurrent.Ref
import cats.syntax._
import cats.implicits._
import fs2._
import cats.effect.{ Async, Concurrent, Effect, Sync, Timer }
import fs2.concurrent.{ InspectableQueue, Queue, Signal }
import fs2.io.tcp.Socket
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.duration.FiniteDuration
import cyborg.bonus._
import scala.concurrent.duration._

import cyborg.DspRegisters._
import cyborg.MEAMEmessages._

import cyborg.dsp.calls.DspCalls._

// import cyborg.HttpClient._
import cyborg.utilz._

object mockDSP {

  sealed trait ElectrodeState
  case object Firing   extends ElectrodeState
  case object Idle     extends ElectrodeState
  case object Inactive extends ElectrodeState

  sealed trait Event
  case class StartFiring(idx: Int, tick: Int)           extends Event
  case class DoneFiring(idx: Int, tick: Int)            extends Event
  case class DSPError(idx: Int, msg: String, tick: Int) extends Event

  val fireTickLength = 400

  case class StimReq(idx: Int, state: ElectrodeState, period: Int, nextEventTimestep: Int){
    def updatePeriod(nextPeriod: Int, currentTick: Int) = {
      val diff = nextPeriod - period
      val newFiringTimestep = nextEventTimestep + diff
      val next = if (newFiringTimestep <= currentTick)
        StimReq(idx, state, nextPeriod, currentTick + 1)
      else
        StimReq(idx, state, nextPeriod, newFiringTimestep)

      next
    }


    def toggle(active: Boolean, currentTick: Int) = {
      if(active)
        StimReq(idx, Idle, period, currentTick + 1)
      else
        StimReq(idx, Inactive, period, nextEventTimestep)
    }


    def runTick(tick: Int): (Option[Event], StimReq) = {
      state match {
        case Firing if tick == nextEventTimestep => {
          (Some(DoneFiring(idx, tick)), StimReq(idx, Idle, period, (tick - fireTickLength) + period))
        }
        case Idle if tick == nextEventTimestep => {
          (Some(StartFiring(idx, tick)), StimReq(idx, Firing, period, tick + fireTickLength))
        }
        case _ => {
          (None, StimReq(idx, state, period, nextEventTimestep))
        }
      }
    }
    override def toString = s"StimReq $idx. state: $state, period: $period, nextEventTimestep: $nextEventTimestep"
  }


  case class DSPstate(m: Map[Int, StimReq], currentTick: Int){

    def nextEvent: Option[Int] = {
      val filteredNext = m.values.filter{
        _.state match {
          case Inactive => false
          case _ => true
        }
      }.filter(_.period > 0)
      val next = filteredNext.map(_.nextEventTimestep).toSeq.minByOption
      next
    }

    def ticksToNextEvent: Option[Int] = nextEvent.map(_ - currentTick)

    def updatePeriod(groupIdx: Int, nextPeriod: Int) = DSPstate(
      m.updated(groupIdx, m(groupIdx).updatePeriod(nextPeriod, currentTick)), currentTick)

    def toggleGroup(groupIdx: Int, toggle: Boolean)  = DSPstate(
      m.updated(groupIdx, m(groupIdx).toggle(toggle, currentTick)), currentTick)

    def runTick(tick: Int): (List[Event], DSPstate) = {
      val (messages, next) = m.toList.map{ case(idx, stimReq) =>
        val (events, next) = stimReq.runTick(tick)
        (events, next)
      }.unzip
      (messages.flatten, DSPstate(next.zipIndexLeft.toMap, tick))
    }
  }
  object DSPstate {
    def init = DSPstate( (0 to 2).map( x => (x, StimReq(x, Inactive, 0,0))).toMap, 0)

    def runTicks(dsp: DSPstate, ticks: Int): (List[Event], DSPstate) = {
      def go(dsp: DSPstate, ticks: Int, log: List[Event]): (List[Event], DSPstate) = {
        dsp.ticksToNextEvent match {
          case Some(ticksToNext) if (ticksToNext < ticks) => {
            val nextTick = dsp.currentTick + ticksToNext
            val (events, next) = dsp.runTick(nextTick)
            go(next, ticks - ticksToNext, events ::: log)
          }
          case Some(ticksToNext) => {
            (log.reverse, dsp.copy(currentTick = dsp.currentTick + ticks))
          }
          case _ => {
            (log.reverse, dsp.copy(currentTick = dsp.currentTick + ticks))
          }
        }
      }
      go(dsp, ticks, Nil)
    }
  }


  def decodeDspCall(call: DspFuncCall): DSPstate => DSPstate = {

    def idx = call.args.toMap.apply(STIM_QUEUE_GROUP)
    def nextPeriod = call.args.toMap.apply(STIM_QUEUE_PERIOD)

    call.func match {
      case  DUMP                            => state => state
      case  RESET                           => state => DSPstate.init
      case  CONFIGURE_ELECTRODE_GROUP       => state => state
      case  SET_ELECTRODE_GROUP_MODE        => state => state
      case  HEALTH_CHECK                    => state => state
      case  COMMIT_CONFIG                   => state => state
      case  START_STIM_QUEUE                => state => state
      case  STOP_STIM_QUEUE                 => state => state

      case  SET_ELECTRODE_GROUP_PERIOD      => state => {
        state.copy(m = state.m.updateAt(idx)(_.updatePeriod(nextPeriod, state.currentTick)))
      }

      case  ENABLE_STIM_GROUP               => state =>
        state.copy(m = state.m.updateAt(idx)(_.toggle(true, state.currentTick)))

      case  DISABLE_STIM_GROUP              => state =>
        state.copy(m = state.m.updateAt(idx)(_.toggle(false, state.currentTick)))

      case  COMMIT_CONFIG_DEBUG             => state => state
      case  WRITE_SQ_DEBUG                  => state => state
      case  SET_BLANKING                    => state => state
      case  SET_BLANKING_PROTECTION         => state => state
    }
  }


  def startDSP(
    messages: Ref[IO, Chain[DspFuncCall]], resolution: FiniteDuration): Stream[IO,Event] = {

    import backendImplicits._
    import cyborg.dsp.calls.DspCalls.FiniteDurationDSPext

    /**
      For hvert tick, kjør dsp emulatoren en gang
      Før kjøring hentes nye oppdateringer fra msg kø
      */
    def go(tickSource: Stream[IO, Unit], dsp: DSPstate): Pull[IO, Event, Unit] = {
      tickSource.pull.uncons1.flatMap {
        case Some((_, tl)) => {
          Pull.eval(messages.getAndSet(Chain.nil)) flatMap { funcCalls =>
            val updatedDsp: DSPstate = funcCalls.foldLeft(dsp){ case(dsp, call) =>
              decodeDspCall(call)(dsp)
            }
            val (msg, next) = DSPstate.runTicks(updatedDsp, resolution.toDSPticks)
            Pull.output(Chunk.seq(msg)) >> go(tl, next)
          }
        }
        case None => Pull.done
      }
    }

    val tickSource = Stream.fixedRate(resolution)
    go(tickSource, DSPstate.init).stream
  }
}
