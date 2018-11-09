package cyborg

import cats.effect._
import cats._
import cats.syntax._
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.{ Queue, Signal }
import fs2.io.tcp.Socket
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import cyborg.bonus._

import cyborg.HttpClient._
import cyborg.utilz._

object mockDSP {

  sealed trait ElectrodeState
  case object Firing   extends ElectrodeState
  case object Idle     extends ElectrodeState
  case object Inactive extends ElectrodeState

  sealed trait Event
  case object StartFiring extends Event
  case object DoneFiring  extends Event
  case object DSPError    extends Event

  val fireTickLength = 400

  case class StimReq(state: ElectrodeState, period: Int, nextEventTimestep: Int){
    def updatePeriod(nextPeriod: Int, currentTick: Int) = {
      val diff = nextPeriod - period
      val newFiringTimestep = nextEventTimestep + diff
      if (newFiringTimestep <= currentTick)
        StimReq(state, nextPeriod, currentTick + 1)
      else
        StimReq(state, nextPeriod, newFiringTimestep)
    }


    def toggle(active: Boolean, currentTick: Int) = {
      if(active)
        StimReq(Idle, period, currentTick + nextEventTimestep)
      else
        StimReq(Inactive, period, nextEventTimestep)
    }


    def runTick(tick: Int): (Option[Event], StimReq) =
      state match {
        case Firing if tick == nextEventTimestep =>
          (Some(DoneFiring), StimReq(Idle, period, (tick - fireTickLength) + period))
        case Idle if tick == nextEventTimestep =>
          (Some(StartFiring), StimReq(Firing, period, tick + fireTickLength))
        case _ =>
          (None, StimReq(state, period, nextEventTimestep))
      }
  }
  object StimReq { def init = StimReq(Inactive, 0, 0) }


  case class DSPstate(m: Map[Int, StimReq], tick: Int){

    def updatePeriod(groupIdx: Int, nextPeriod: Int) = DSPstate(
      m.updated(groupIdx, m(groupIdx).updatePeriod(nextPeriod, tick)), tick)

    def toggleGroup(groupIdx: Int, toggle: Boolean)  = DSPstate(
      m.updated(groupIdx, m(groupIdx).toggle(toggle, tick)), tick)

    // TODO add something more substantial than .toString
    def runTick: (List[String], DSPstate) = {
      val (messages, next) = m.toList.map{ case(idx, stimReq) =>
        val (event, next) = stimReq.runTick(tick)
        (event.map(_.toString()), next)
      }.unzip
      (messages.flatten, DSPstate(next.zipIndexLeft.toMap, tick + 1))
    }

    def fastForward: DSPstate = {
      val nextEvent = m.mapValues(_.nextEventTimestep).values.min
      DSPstate(m, nextEvent)
    }
  }
  object DSPstate { def init = DSPstate( (0 to 2).map( x => (x, StimReq.init)).toMap, 0) }


  def startDSP(requests: Stream[IO, HttpClient.DspFuncCall]): IO[Stream[IO,String]] = {

    def decodeDspCall(call: HttpClient.DspFuncCall): DSPstate => DSPstate = {
      import cyborg.dsp.calls.DspCalls._
      import cyborg.DspRegisters._

      def idx = call.args.toMap.apply(STIM_QUEUE_GROUP)
      def nextPeriod = call.args.toMap.apply(STIM_QUEUE_PERIOD)

      call.func match {
        case  DUMP                            => ???
        case  RESET                           => state => DSPstate.init
        case  CONFIGURE_ELECTRODE_GROUP       => ???
        case  SET_ELECTRODE_GROUP_MODE_MANUAL => ???
        case  SET_ELECTRODE_GROUP_MODE_AUTO   => ???
        case  COMMIT_CONFIG                   => ???
        case  START_STIM_QUEUE                => ???
        case  STOP_STIM_QUEUE                 => ???

        case  SET_ELECTRODE_GROUP_PERIOD      => state =>
          state.copy(m = state.m.updateAt(idx)(_.updatePeriod(nextPeriod, state.tick)))

        case  ENABLE_STIM_GROUP               => state =>
          state.copy(m = state.m.updateAt(idx)(_.toggle(true, state.tick)))

        case  DISABLE_STIM_GROUP              => state =>
          state.copy(m = state.m.updateAt(idx)(_.toggle(false, state.tick)))

        case  COMMIT_CONFIG_DEBUG             => ???
        case  WRITE_SQ_DEBUG                  => ???
      }
    }

    ???
  }
}
