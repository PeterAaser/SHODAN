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

  val DUMP                            = 1
  val RESET                           = 2
  val CONFIGURE_ELECTRODE_GROUP       = 3
  val SET_ELECTRODE_GROUP_MODE_MANUAL = 4
  val SET_ELECTRODE_GROUP_MODE_AUTO   = 5
  val COMMIT_CONFIG                   = 6
  val START_STIM_QUEUE                = 7
  val STOP_STIM_QUEUE                 = 8
  val SET_ELECTRODE_GROUP_PERIOD      = 9
  val ENABLE_STIM_GROUP               = 10
  val DISABLE_STIM_GROUP              = 11


  implicit class FiniteDurationDSPext(t: FiniteDuration) {
    def toDSPticks = (t.toMicros/20).toInt
  }


  def configureStimGroup(group: Int, electrodes: List[Int]) = {
    val elec0 = electrodes.filter(_ > 30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
    val elec1 = electrodes.filterNot(_ > 30).map(_-30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
    dspCall( CONFIGURE_ELECTRODE_GROUP,
             elec0  -> STIM_QUEUE_ELEC0,
             elec1  -> STIM_QUEUE_ELEC1).void
  }


  // hurr stringz
  def setElectrodeModes(mode: String) =
    if(mode == "manual")
      dspCall( SET_ELECTRODE_GROUP_MODE_MANUAL ).void
    else
      dspCall( SET_ELECTRODE_GROUP_MODE_AUTO ).void

  val commitConfig =
    dspCall( COMMIT_CONFIG ).void

  val startStimQueue: IO[Unit] =
    dspCall(START_STIM_QUEUE).void

  val stopStimQueue: IO[Unit] =
    dspCall(STOP_STIM_QUEUE).void

  val resetStimQueue =
    dspCall(RESET).void

  def stimGroupChangePeriod(group: Int, period: Int): IO[Unit] = {
    dspCall(SET_ELECTRODE_GROUP_PERIOD,
            group -> STIM_QUEUE_GROUP,
            period -> STIM_QUEUE_PERIOD ).void
  }


  def enableStimGroup(group: Int): IO[Unit] = {
    dspCall(ENABLE_STIM_GROUP,
            group -> STIM_QUEUE_GROUP ).void
  }

  def disableStimGroup(group: Int): IO[Unit] =
    dspCall(DISABLE_STIM_GROUP,
            group -> STIM_QUEUE_GROUP ).void

  def defaultSetup: IO[Unit] = for {
    _ <- IO { say("configuring the default stim setup with stim only on electrode 1 through 3 for testing") }
    _ <- IO { say("resetting") }
    _ <- stopStimQueue
    _ <- resetStimQueue
    _ <- IO { say("configuring...") }
    _ <- setElectrodeModes("auto")
    _ <- configureStimGroup(0, List(1,2,3))
    _ <- stimGroupChangePeriod(0, 4000)
    _ <- enableStimGroup(0)
    _ <- IO { say("stim queue set up, committing config") }
    _ <- IO { say("keep in mind that stim waveform might not be set up yet!") }
    _ <- commitConfig
    _ <- IO { say("starting stim queue") }
    _ <- startStimQueue
  } yield ()


  def uploadWave(upload: IO[Unit]): IO[Unit] = for {
    _ <- stopStimQueue
    _ <- upload
  } yield ()


  def uploadSquareTest(period: FiniteDuration, amplitude: Int): IO[Unit] = for {
    _ <- IO { say(s"Uploading square wave. period: $period, amplitude: ${amplitude}mV") }
    _ <- uploadWave( waveformGenerator.squareWave(0, (period/2), (period/2), 0, amplitude) )
  } yield ()


  def uploadSineTest(period: FiniteDuration, amplitude: Int): IO[Unit] = for {
    _ <- IO { say(s"Uploading square wave. period: $period, amplitude: ${amplitude}mV") }
    _ <- uploadWave( waveformGenerator.sineWave(0, period, amplitude) )
  } yield ()
}
