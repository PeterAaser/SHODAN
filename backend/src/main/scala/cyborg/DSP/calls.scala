package cyborg

import HttpClient._
import fs2._
import cats._
import cats.syntax._
import cats.implicits._
import cats.effect.implicits._
import cats.effect._
import DspRegisters._
import scala.concurrent.duration._
import utilz._


object DspCalls {

  val DUMP               = 1 // not implemented
  val RESET              = 2 // not implemented
  val STIM_GROUP_REQUEST = 3
  val STIM_DEBUG         = 4 // not implemented
  val START_STIM_QUEUE   = 5
  val STOP_STIM_QUEUE    = 6
  val SLOW_MODE          = 7
  val STIM_REQUEST       = 8
  val ENABLE_STIM_GROUP  = 9
  val NO_OP              = 10

  implicit class FiniteDurationDSPext(t: FiniteDuration) {
    def toDSPticks = (t.toMicros/20).toInt
  }


  // Alters the period of a stim group
  def stimGroupRequest(group: Int, period: Int): IO[Unit] = {
    dspCall(STIM_REQUEST,
            period -> STIM_QUEUE_PERIOD ).void
  }


  // Alters the period and selected electrodes of a stim group
  def stimRequest(group: Int, period: Int, electrodes: List[Int]) = {
    val elec0 = electrodes.filter(_ > 30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
    val elec1 = electrodes.filterNot(_ > 30).map(_-30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
    dspCall( STIM_GROUP_REQUEST,
             period -> STIM_QUEUE_PERIOD,
             elec0  -> STIM_QUEUE_ELEC0,
             elec1  -> STIM_QUEUE_ELEC1).void
  }


  // At the moment the wave upload function in MEAMEutilz creates a bunch of writes
  // This is the safer variant, use this one instead please
  def uploadWave(upload: IO[Unit]): IO[Unit] = for {
    _ <- stopStimQueue
    _ <- upload
  } yield ()



  def setSlowMode(factor: Int) =
    dspCall(SLOW_MODE,
            factor -> SLOW_MODE_FACTOR).void

  def setSlowModeOff = setSlowMode(0).void


  // From a set of distances from the agent, create stimulus requests
  // Doesn't do much at all if the groups are not set up on the DSP.
  def createStimRequests(dists: List[Double]): IO[Unit] = {
    import MEAMEutilz._
    val periods = dists.map(toFreq).map(toTickPeriod)
    val requests = periods.zipWithIndex.map{ case(period, idx) => stimGroupRequest(idx, period) }

    // while only applicative is needed for sequence, with IO this will happen sequentially.
    requests.sequence_
  }

  def startStimQueue: IO[Unit] =
    dspCall(START_STIM_QUEUE).void

  def stopStimQueue: IO[Unit] =
    dspCall(STOP_STIM_QUEUE).void


  def enableStimGroup(sgIdx: Int): IO[Unit] =
    dspCall(ENABLE_STIM_GROUP,
            sgIdx -> STIM_QUEUE_TOGGLE_SG,
            1     -> STIM_QUEUE_TOGGLE_VAL).void


  def disableStimGroup(sgIdx: Int): IO[Unit] =
    dspCall(ENABLE_STIM_GROUP,
            sgIdx -> STIM_QUEUE_TOGGLE_SG,
            0     -> STIM_QUEUE_TOGGLE_VAL).void


  def checkDsp: IO[Boolean] =
    dspCall(NO_OP).attempt.map{
      case Right(_) => true
      case Left(_)  => false
    }
}
