package cyborg.dsp.calls
import cyborg._

import HttpClient._
import cyborg.twiddle.Reg
import fs2._
import cats._
import cats.syntax._
import cats.implicits._
import cats.effect.implicits._
import cats.effect._
import DspRegisters._
import scala.concurrent.duration._
import utilz._
import bonus._
import BitDrawing._

import dsp.stimulus.WaveformGenerator
import HttpClient.DSP._

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
  val COMMIT_CONFIG_DEBUG             = 12
  val WRITE_SQ_DEBUG                  = 13

  implicit class FiniteDurationDSPext(t: FiniteDuration) {
    def toDSPticks = (t.toMicros/20).toInt
  }

  implicit class DSPIntOps(i: Int) {
    def fromDSPticks: FiniteDuration = (i*20).micros
  }


  def configureStimGroup(group: Int, electrodes: List[Int]) = {
    val elec0 = electrodes.filter(_ <= 30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
    val elec1 = electrodes.filterNot(_ <= 30).map(_-30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
    for {
      _ <- dspCall( CONFIGURE_ELECTRODE_GROUP,
                    elec0  -> STIM_QUEUE_ELEC0,
                    elec1  -> STIM_QUEUE_ELEC1).void
      _ <- Fsay[IO](s"Enabling electrodes for stim group $group (dsp call $CONFIGURE_ELECTRODE_GROUP)")
      _ <- Fsay[IO](s"elec0: $elec0")
      _ <- Fsay[IO](s"elec1: $elec1")
    } yield ()
  }

  // hurr stringz
  def setElectrodeModes(mode: String) = {
    val callNumber =
      if(mode == "manual")
        SET_ELECTRODE_GROUP_MODE_MANUAL
      else
        SET_ELECTRODE_GROUP_MODE_AUTO
    for {
      _ <- dspCall(callNumber)
      _ <- Fsay[IO](s"setting electrode mode to $mode (dsp call $callNumber)")
    } yield ()
  }

  val commitConfig = for {
    _ <- dspCall( COMMIT_CONFIG ).void
    _ <- Fsay[IO](s"commiting config (dsp call $COMMIT_CONFIG)")
  } yield ()

  val startStimQueue: IO[Unit] = for {
    _ <- dspCall(START_STIM_QUEUE).void
    _ <- Fsay[IO](s"start stim queue (dsp call $START_STIM_QUEUE)\nSTIM QUEUE IS NOW LIVE! #YOLO")
  } yield ()

  val stopStimQueue: IO[Unit] = for {
    _ <- dspCall(STOP_STIM_QUEUE).void
    _ <- Fsay[IO](s"Stop stim queue (dsp call $STOP_STIM_QUEUE)")
  } yield ()

  val resetStimQueue: IO[Unit] = for {
    _ <- dspCall(RESET).void
    _ <- Fsay[IO](s"reset stim queue (dsp call $RESET")
  } yield ()

  def stimGroupChangePeriod(group: Int, period: FiniteDuration): IO[Unit] = for {
    _ <- dspCall(SET_ELECTRODE_GROUP_PERIOD,
                 group -> STIM_QUEUE_GROUP,
                 period.toDSPticks -> STIM_QUEUE_PERIOD ).void
    _ <- Fsay[IO](s"stim group change period, group $group to $period (${period}) (dsp call $SET_ELECTRODE_GROUP_PERIOD")
  } yield ()


  def enableStimReqGroup(group: Int): IO[Unit] = for {
    _ <- dspCall(ENABLE_STIM_GROUP,
                 group -> STIM_QUEUE_GROUP ).void
    _ <- Fsay[IO](s"enabling stim group $group (dsp call $ENABLE_STIM_GROUP)")
  } yield ()

  def disableStimReqGroup(group: Int): IO[Unit] = for {
    _ <- dspCall(DISABLE_STIM_GROUP,
                 group -> STIM_QUEUE_GROUP ).void
    _ <- Fsay[IO](s"disabling stim group $group (dsp call $DISABLE_STIM_GROUP)")
  } yield ()



  def defaultSetup: IO[Unit] = for {
    _ <- stopStimQueue
    _ <- resetStimQueue
    _ <- uploadSquareTest(20.millis, 20)
    _ <- setElectrodeModes("manual")
    _ <- configureStimGroup(0, List(1,2,3))
    _ <- stimGroupChangePeriod(0, 500.millis)
    _ <- enableStimReqGroup(0)
    _ <- sayElectrodeConfig
    _ <- commitConfig
    _ <- sayElectrodeConfig
    _ <- startStimQueue
  } yield ()


  def uploadWave(upload: IO[Unit]): IO[Unit] = for {
    _ <- stopStimQueue
    _ <- upload
  } yield ()


  def uploadSquareTest(period: FiniteDuration, amplitude: mV): IO[Unit] = for {
    _ <- IO { say(s"Uploading square wave. period: $period, amplitude: ${amplitude}mV") }
    _ <- uploadWave( WaveformGenerator.squareWave(0, (period/2), (period/2), 0, amplitude) )
  } yield ()


  def uploadSineTest(period: FiniteDuration, amplitude: Int): IO[Unit] = for {
    _ <- IO { say(s"Uploading square wave. period: $period, amplitude: ${amplitude}mV") }
    _ <- uploadWave( WaveformGenerator.sineWave(0, period, amplitude) )
  } yield ()


  val readElectrodeConfig: IO[String] = for {
    _ <- dspCall(COMMIT_CONFIG_DEBUG)
    config <- readRegistersRequest(
      RegisterReadList(List(
                         CFG_DEBUG_ELEC0,
                         CFG_DEBUG_ELEC1,
                         CFG_DEBUG_MODE0,
                         CFG_DEBUG_MODE1,
                         CFG_DEBUG_MODE2,
                         CFG_DEBUG_MODE3,
                         CFG_DEBUG_DAC0,
                         CFG_DEBUG_DAC1,
                         CFG_DEBUG_DAC2,
                         CFG_DEBUG_DAC3)))
    } yield {
      List(
        "ELECTRODE CFG IS:",
        s"CFG_DEBUG_ELEC0 <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_ELEC0)).w)}",
        s"CFG_DEBUG_ELEC1 <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_ELEC1)).w)}",
        s"CFG_DEBUG_MODE0 <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_MODE0)).w)}",
        s"CFG_DEBUG_MODE1 <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_MODE1)).w)}",
        s"CFG_DEBUG_MODE2 <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_MODE2)).w)}",
        s"CFG_DEBUG_MODE3 <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_MODE3)).w)}",
        s"CFG_DEBUG_DAC0  <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_DAC0 )).w)}",
        s"CFG_DEBUG_DAC1  <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_DAC1 )).w)}",
        s"CFG_DEBUG_DAC2  <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_DAC2 )).w)}",
        s"CFG_DEBUG_DAC3  <- ${as32BinarySpaced(config.asMap(Reg(CFG_DEBUG_DAC3 )).w)}"
      ).mkString("\n","\n","\n")
    }


  val readDebug: IO[Unit] = for {
    dbg <- readRegistersRequest(
      RegisterReadList(
        List(
          DEBUG1, 
          DEBUG2,
          DEBUG3,
          DEBUG4
        )))
    _ <- Fsay[IO]{
      s"\nhere's some debug:\n$dbg"
    }
  } yield ()


  val readStimQueueState: IO[Unit] = for {
    _ <- dspCall(WRITE_SQ_DEBUG)
    electrodes <- readRegistersRequest(
      RegisterReadList(
        List(
          STIM_REQ1_ACTIVE,
          STIM_REQ1_PERIOD,
          STIM_REQ1_NEXT_FIRING_TIMESTEP,

          STIM_REQ2_ACTIVE,
          STIM_REQ2_PERIOD,
          STIM_REQ2_NEXT_FIRING_TIMESTEP,

          STIM_REQ3_ACTIVE,
          STIM_REQ3_PERIOD,
          STIM_REQ3_NEXT_FIRING_TIMESTEP
        )))
    _ <- Fsay[IO]{
      List(
        "STIM QUEUE CFG IS:",
        s"SQ 1: Active:\t\t${electrodes.asMap(Reg(STIM_REQ1_ACTIVE)).w}",
        s"SQ 1: Period:\t\t${electrodes.asMap(Reg(STIM_REQ1_PERIOD)).w}",
        s"SQ 1: next step:\t${electrodes.asMap(Reg(STIM_REQ1_NEXT_FIRING_TIMESTEP)).w}\n",
        s"SQ 2: Active:\t\t${electrodes.asMap(Reg(STIM_REQ2_ACTIVE)).w}",
        s"SQ 2: Period:\t\t${electrodes.asMap(Reg(STIM_REQ2_PERIOD)).w}",
        s"SQ 2: next step:\t${electrodes.asMap(Reg(STIM_REQ2_NEXT_FIRING_TIMESTEP)).w}\n",
        s"SQ 3: Active:\t\t${electrodes.asMap(Reg(STIM_REQ3_ACTIVE)).w}",
        s"SQ 3: Period:\t\t${electrodes.asMap(Reg(STIM_REQ3_PERIOD)).w}",
        s"SQ 3: next step:\t${electrodes.asMap(Reg(STIM_REQ3_NEXT_FIRING_TIMESTEP)).w}\n",
        ).mkString("\n","\n","\n")
    }
  } yield ()


  val sayElectrodeConfig: IO[Unit] = for {
    cfg <- readElectrodeConfig
    _ <- Fsay[IO](cfg)
  } yield ()

  val checkShotsFired: IO[Unit] = for {
    s <- readRegistersRequest(RegisterReadList(List(SHOTS_FIRED)))
    _ <- Fsay[IO](s"shots fired: $s")
  } yield ()

  val checkForErrors: IO[Unit] = for {
    error <- readRegistersRequest(RegisterReadList(List(ERROR_FLAG)))
    _ <- if(error.values.head != 0)
    Fsay[IO](s"error code:\n${lookupErrorName(error.values.head)}")
         else
           Fsay[IO]("no errors")
  } yield ()

  val checkSteps: IO[Unit] = for {
    steps <- readRegistersRequest(RegisterReadList(List(STEP_COUNTER)))
    _ <- Fsay[IO](s"steps taken $steps")
  } yield ()


  def lookupErrorName(errorCode: Int): String = {
    val errorCodes = Map(
      0 -> "No error",
      1 -> "Manual trigger error",
      2 -> "Illegal mode",
      3 -> "zero period trigger",
      4 -> "read request out of bounds",
      5 -> "xzibit error"
    )

    errorCodes.lift(errorCode).map(x => s"$errorCode -> $x")getOrElse(s"Error code $errorCode is not recognized.")
  }
}
