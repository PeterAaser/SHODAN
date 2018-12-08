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


  val DUMP                                 = 1
  val RESET                                = 2
  val CONFIGURE_ELECTRODE_GROUP            = 3
  val SET_ELECTRODE_GROUP_MODE             = 4
  /// mystery!
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
                    group -> STIM_QUEUE_GROUP,
                    elec0  -> STIM_QUEUE_ELEC0,
                    elec1  -> STIM_QUEUE_ELEC1).void
      _ <- Fsay[IO](s"Enabling electrodes for stim group $group (dsp call $CONFIGURE_ELECTRODE_GROUP)")
      _ <- Fsay[IO](s"elec0: ${elec0.asBinarySpaced}")
      _ <- Fsay[IO](s"elec1: ${elec1.asBinarySpaced}")
    } yield ()
  }


  /**
    Toggles electrode mode to auto for all listed electrodes.
    An empty list signifies setting all electrodes to manual (0)
    */
  def setElectrodeModes(electrodes: List[Int]) = {
    val elec0 = electrodes.filter(idx => (idx >= 0  && idx < 15)).foldLeft(0){ case(acc, channel) => acc + (3 << ((channel -  0) * 2)) }
    val elec1 = electrodes.filter(idx => (idx >= 15 && idx < 30)).foldLeft(0){ case(acc, channel) => acc + (3 << ((channel - 15) * 2)) }
    val elec2 = electrodes.filter(idx => (idx >= 30 && idx < 45)).foldLeft(0){ case(acc, channel) => acc + (3 << ((channel - 30) * 2)) }
    val elec3 = electrodes.filter(idx => (idx >= 45 && idx < 60)).foldLeft(0){ case(acc, channel) => acc + (3 << ((channel - 45) * 2)) }

    say("set elec mode payload is:")
    say(s"elec0: ${elec0.asBinarySpaced}")
    say(s"elec1: ${elec1.asBinarySpaced}")
    say(s"elec2: ${elec2.asBinarySpaced}")
    say(s"elec3: ${elec3.asBinarySpaced}")

    dspCall(SET_ELECTRODE_GROUP_MODE,
        elec0 -> ELECTRODE_MODE_ARG1,
        elec1 -> ELECTRODE_MODE_ARG2,
        elec2 -> ELECTRODE_MODE_ARG3,
        elec3 -> ELECTRODE_MODE_ARG4)
  }

  val commitConfig = for {
    _ <- Fsay[IO](s"commiting config (dsp call $COMMIT_CONFIG)")
    _ <- dspCall( COMMIT_CONFIG ).void
  } yield ()

  val startStimQueue: IO[Unit] = for {
    _ <- Fsay[IO](s"start stim queue (dsp call $START_STIM_QUEUE)\nSTIM QUEUE IS NOW LIVE! #YOLO")
    _ <- dspCall(START_STIM_QUEUE).void
  } yield ()

  val stopStimQueue: IO[Unit] = for {
    _ <- Fsay[IO](s"Stop stim queue (dsp call $STOP_STIM_QUEUE)")
    _ <- dspCall(STOP_STIM_QUEUE).void
  } yield ()

  val resetStimQueue: IO[Unit] = for {
    _ <- Fsay[IO](s"reset stim queue (dsp call $RESET")
    _ <- dspCall(RESET).void
  } yield ()

  def stimGroupChangePeriod(group: Int, period: FiniteDuration): IO[Unit] = for {
    _ <- Fsay[IO](s"stim group change period, group $group to ${period.toMillis}ms (dsp call $SET_ELECTRODE_GROUP_PERIOD)")
    _ <- dspCall(SET_ELECTRODE_GROUP_PERIOD,
                 group -> STIM_QUEUE_GROUP,
                 period.toDSPticks -> STIM_QUEUE_PERIOD ).void
  } yield ()


  def enableStimReqGroup(group: Int): IO[Unit] = for {
    _ <- Fsay[IO](s"enabling stim group $group (dsp call $ENABLE_STIM_GROUP)")
    errors <- checkForErrors
    _ <- errors match {
      case Some(s) => Fsay[IO](s"error: $s")
      case None    => Fsay[IO](s"No DSP error flags raised")
    }
    _ <- dspCall(ENABLE_STIM_GROUP,
                 group -> STIM_QUEUE_GROUP ).void
  } yield ()

  def disableStimReqGroup(group: Int): IO[Unit] = for {
    _ <- Fsay[IO](s"disabling stim group $group (dsp call $DISABLE_STIM_GROUP)")
    _ <- dspCall(DISABLE_STIM_GROUP,
                 group -> STIM_QUEUE_GROUP ).void
  } yield ()


  val setup: Setting.ExperimentSettings => IO[Unit] = config =>
  for {
    _ <- Fsay[IO](s"Flashing DSP")
    _ <- cyborg.dsp.DSP.flashDSP
    _ <- Fsay[IO](s"Stopping and resetting stim Q")
    _ <- stopStimQueue
    _ <- resetStimQueue

    _ <- Fsay[IO](s"Configuring blanking & blanking protection")
    _ <- setBlanking((0 to 59).toList)
    _ <- setBlankingProtection((0 to 59).toList)

    _ <- Fsay[IO](s"Uploading stimulus")
    _ <- uploadSquareTest(10.millis, 200)

    // _ <- Fsay[IO](s"Reading debug config")
    // _ <- cyborg.dsp.DSP.configureElectrodes(config)
    // s <- readElectrodeConfig
    // _ <- Fsay[IO](s"$s")

    _ <- Fsay[IO](s"Committing config, we're LIVE")
    _ <- commitConfig
    _ <- startStimQueue
  } yield ()



  def uploadWave(upload: IO[Unit]): IO[Unit] = for {
    _ <- stopStimQueue
    _ <- upload
  } yield ()


  def uploadSquareTest(period: FiniteDuration, amplitude: mV): IO[Unit] = for {
    _ <- Fsay[IO](s"Uploading balanced square wave. period: $period, amplitude: ${amplitude}mV")
    _ <- uploadWave( WaveformGenerator.balancedSquareWave(0, 2.millis, 200))
    _ <- uploadWave( WaveformGenerator.balancedSquareWave(1, 2.millis, 200))
    _ <- uploadWave( WaveformGenerator.balancedSquareWave(2, 2.millis, 200))
  } yield ()


  def uploadSineTest(period: FiniteDuration, amplitude: Int): IO[Unit] = for {
    _ <- Fsay[IO](s"Uploading square wave. period: $period, amplitude: ${amplitude}mV")
    _ <- uploadWave( WaveformGenerator.sineWave(0, period, amplitude) )
  } yield ()


  val readElectrodeConfig: IO[String] = for {
    _ <- Fsay[IO](s"reading electrode config")
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

  val checkForErrors: IO[Option[String]] = for {
    error <- readRegistersRequest(RegisterReadList(List(ERROR_FLAG)))
  } yield {
      if(error.values.head == 0) None
      else Some(lookupErrorName(error.values.head))
  }

  val checkSteps: IO[Unit] = for {
    steps <- readRegistersRequest(RegisterReadList(List(STEP_COUNTER)))
    _ <- Fsay[IO](s"steps taken $steps")
  } yield ()


  /**
    Toggles blanking on supplied electrodes. If an empty list is passed
    this is equivalent to untoggling blanking for all electrodes.
    */
  def setBlanking(electrodes: List[Int]): IO[Unit] = {
    val elec0 = electrodes.filter(_ <= 30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
    val elec1 = electrodes.filterNot(_ <= 30).map(_-30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }

    dspCall(SET_BLANKING,
            elec0 -> BLANKING_EN_ELECTRODES1,
            elec1 -> BLANKING_EN_ELECTRODES2)
  }


  /**
    Toggles blanking on supplied electrodes. If an empty list is passed
    this is equivalent to untoggling blanking for all electrodes.
    */
  def setBlankingProtection(electrodes: List[Int]): IO[Unit] = {
    val elec0 = electrodes.filter(_ <= 30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
    val elec1 = electrodes.filterNot(_ <= 30).map(_-30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }

    dspCall(SET_BLANKING_PROTECTION,
            elec0 -> BLANK_PROT_EN_ELECTRODES1,
            elec1 -> BLANK_PROT_EN_ELECTRODES2)
  }


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

  def stimuliRequestSink(allowed: List[Int] = List(0, 1, 2))(implicit ec: EC): Sink[IO, (Int,Option[FiniteDuration])] = {
    def go(s: Stream[IO, (Int,Option[FiniteDuration])], state: Map[Int, Boolean]): Pull[IO, Unit, Unit] = {
      s.pull.uncons1.flatMap {
        case Some(((idx, Some(period)), tl)) if !state(idx) =>
          Pull.eval(stimGroupChangePeriod(idx, period)) >>
            Pull.eval(enableStimReqGroup(idx)) >>
            go(tl, state.updated(idx, true))

        case Some(((idx, None), tl)) if state(idx) =>
          Pull.eval(disableStimReqGroup(idx)) >>
            go(tl, state.updated(idx, false))

        case Some(((idx, Some(period)), tl)) =>
          Pull.eval(stimGroupChangePeriod(idx, period)) >>
            go(tl, state)

        case Some((_, tl)) => go(tl, state)

        case None => Pull.done
      }
    }

    ins => go(
      ins.filter{ case(idx, req) => allowed.contains(idx)},
      allowed.map((_, false)).toMap
    ).stream
  }
}
