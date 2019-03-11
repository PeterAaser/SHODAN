package cyborg.dsp.calls
import cats.data.Kleisli
import cyborg._

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

object DspCalls {

  val DUMP                                 = 1
  val RESET                                = 2
  val CONFIGURE_ELECTRODE_GROUP            = 3
  val SET_ELECTRODE_GROUP_MODE             = 4
  val HEALTH_CHECK                         = 5
  val COMMIT_CONFIG                        = 6
  val START_STIM_QUEUE                     = 7
  val STOP_STIM_QUEUE                      = 8
  val SET_ELECTRODE_GROUP_PERIOD           = 9
  val ENABLE_STIM_GROUP                    = 10
  val DISABLE_STIM_GROUP                   = 11
  val COMMIT_CONFIG_DEBUG                  = 12
  val WRITE_SQ_DEBUG                       = 13
  val SET_BLANKING                         = 14
  val SET_BLANKING_PROTECTION              = 15

  /**
    conversion from time to ticks and vice versa
    */
  implicit class FiniteDurationDSPext(t: FiniteDuration) { def toDSPticks = (t.toMicros/20).toInt }
  implicit class DSPIntOps(i: Int) { def fromDSPticks: FiniteDuration = (i*20).micros }
}

class DspCalls[F[_]: Sync](client: MEAMEHttpClient[F], waveformGenerator: WaveformGenerator[F]) {

  import DspCalls._
  import client.DSP._


  val commitConfig = for {
    _ <- Fsay[F](s"commiting config (dsp call $COMMIT_CONFIG)")
    _ <- dspCall( COMMIT_CONFIG ).void
  } yield ()

  val startStimQueue: F[Unit] = for {
    _ <- Fsay[F](s"start stim queue (dsp call $START_STIM_QUEUE)\nSTIM QUEUE IS NOW LIVE! #YOLO")
    _ <- dspCall(START_STIM_QUEUE).void
  } yield ()

  val stopStimQueue: F[Unit] = for {
    _ <- Fsay[F](s"Stop stim queue (dsp call $STOP_STIM_QUEUE)")
    _ <- dspCall(STOP_STIM_QUEUE).void
  } yield ()

  val resetStimQueue: F[Unit] = for {
    _ <- Fsay[F](s"reset stim queue (dsp call $RESET)")
    _ <- dspCall(RESET).void
  } yield ()

  def stimGroupChangePeriod(group: Int, period: FiniteDuration): F[Unit] = for {
    _ <- dspCall(SET_ELECTRODE_GROUP_PERIOD,
                 group -> STIM_QUEUE_GROUP,
                 period.toDSPticks -> STIM_QUEUE_PERIOD ).void
  } yield ()

  def enableStimReqGroup(group: Int): F[Unit] = for {
    // _ <- Fsay[F](s"enable stim group $group (dsp call $ENABLE_STIM_GROUP)")
    _ <- dspCall(ENABLE_STIM_GROUP,
                 group -> STIM_QUEUE_GROUP ).void
  } yield ()

  def disableStimReqGroup(group: Int): F[Unit] = for {
    _ <- dspCall(DISABLE_STIM_GROUP,
                 group -> STIM_QUEUE_GROUP ).void
  } yield ()

  def uploadWave(upload: F[Unit]): F[Unit] = for {
    // _ <- stopStimQueue
    _ <- upload
  } yield ()

  def uploadSquareTest(period: FiniteDuration, amplitude: mV): F[Unit] = for {
    _ <- Fsay[F](s"Uploading balanced square wave. period: $period, amplitude: ${amplitude}mV")
    _ <- uploadWave( waveformGenerator.balancedSquareWave(0, period, amplitude))
    _ <- uploadWave( waveformGenerator.balancedSquareWave(1, period, amplitude))
    _ <- uploadWave( waveformGenerator.balancedSquareWave(2, period, amplitude))
  } yield ()

  def uploadSineTest(period: FiniteDuration, amplitude: Int): F[Unit] = for {
    _ <- Fsay[F](s"Uploading square wave. period: $period, amplitude: ${amplitude}mV")
    _ <- uploadWave( waveformGenerator.sineWave(0, period, amplitude) )
  } yield ()

  val readElectrodeConfig: F[String] = for {
    _ <- Fsay[F](s"reading electrode config")
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
        s"CFG_DEBUG_ELEC0 <- ${config.asMap(Reg(CFG_DEBUG_ELEC0)).w.asBinarySpaced}",
        s"CFG_DEBUG_ELEC1 <- ${config.asMap(Reg(CFG_DEBUG_ELEC1)).w.asBinarySpaced}",
        s"CFG_DEBUG_MODE0 <- ${config.asMap(Reg(CFG_DEBUG_MODE0)).w.asBinarySpaced}",
        s"CFG_DEBUG_MODE1 <- ${config.asMap(Reg(CFG_DEBUG_MODE1)).w.asBinarySpaced}",
        s"CFG_DEBUG_MODE2 <- ${config.asMap(Reg(CFG_DEBUG_MODE2)).w.asBinarySpaced}",
        s"CFG_DEBUG_MODE3 <- ${config.asMap(Reg(CFG_DEBUG_MODE3)).w.asBinarySpaced}",
        s"CFG_DEBUG_DAC0  <- ${config.asMap(Reg(CFG_DEBUG_DAC0 )).w.asBinarySpaced}",
        s"CFG_DEBUG_DAC1  <- ${config.asMap(Reg(CFG_DEBUG_DAC1 )).w.asBinarySpaced}",
        s"CFG_DEBUG_DAC2  <- ${config.asMap(Reg(CFG_DEBUG_DAC2 )).w.asBinarySpaced}",
        s"CFG_DEBUG_DAC3  <- ${config.asMap(Reg(CFG_DEBUG_DAC3 )).w.asBinarySpaced}"
      ).mkString("\n","\n","\n")
    }

  val readDebug: F[Unit] = for {
    dbg <- readRegistersRequest(
      RegisterReadList(
        List(
          DEBUG1,
          DEBUG2,
          DEBUG3,
          DEBUG4
        )))
    _ <- Fsay[F](s"\nhere's some debug:\n$dbg")
  } yield ()

  val readStimQueueState: F[Unit] = for {
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
    _ <- Fsay[F]{
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

  val sayElectrodeConfig: F[Unit] = for {
    cfg <- readElectrodeConfig
    _ <- Fsay[F](cfg)
  } yield ()

  val checkShotsFired: F[Unit] = for {
    s <- readRegistersRequest(RegisterReadList(List(SHOTS_FIRED)))
    _ <- Fsay[F](s"shots fired: $s")
  } yield ()

  val checkForErrors: F[Option[String]] = for {
    error <- readRegistersRequest(RegisterReadList(List(ERROR_FLAG)))
  } yield {
      if(error.values.head == 0) None
      else Some(lookupErrorName(error.values.head))
  }

  val checkSteps: F[Unit] = for {
    steps <- readRegistersRequest(RegisterReadList(List(STEP_COUNTER)))
    _ <- Fsay[F](s"steps taken $steps")
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

  // We all know this is a lie.
  val getDspHealth: F[Boolean] = dspCall(HEALTH_CHECK).map(_ => true).handleErrorWith { e =>
    Fsay[F](s"Warning, get DSP health failed with\n$e", Console.RED).as(false)
  }
}
