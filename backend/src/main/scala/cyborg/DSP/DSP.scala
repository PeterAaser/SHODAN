package cyborg.dsp
import cyborg._

import fs2._
import cats.effect._
import scala.concurrent.duration._
import cyborg.utilz._

import stimulus.WaveformGenerator

object DSP {

  import calls.DspCalls
  import HttpClient.DSP._

  /**
    Setup flashes the DSP, resets state, uploads a test wave, configures the electrodes
    specified by the settings, and starts the stim queue.
    */
  val setup:          Setting.ExperimentSettings => IO[Unit] = DspCalls.setup
  val stopStimQueue:  IO[Unit] = DspCalls.stopStimQueue
  val resetStimQueue: IO[Unit] = DspCalls.resetStimQueue
  val startStimQueue: IO[Unit] = for {
    _ <- DspCalls.commitConfig
    _ <- DspCalls.startStimQueue
  } yield ()

  def setStimgroupPeriod(group: Int, period: FiniteDuration): IO[Unit] =
    DspCalls.stimGroupChangePeriod(group, period)
  def enableStimGroup(group: Int): IO[Unit] =
    DspCalls.enableStimReqGroup(group)
  def disableStimGroup(group: Int): IO[Unit] =
    DspCalls.disableStimReqGroup(group)

  /**
    Resets the device before configuring electrodes.
    Piecewise configuration is therefore not possible.
    */
  def configureElectrodesManual(electrodes: List[List[Int]]): IO[Unit] = {
    for {
      _ <- DspCalls.resetStimQueue
      _ <- DspCalls.configureStimGroup(0, electrodes.lift(0).getOrElse(Nil))
      _ <- DspCalls.configureStimGroup(1, electrodes.lift(1).getOrElse(Nil))
      _ <- DspCalls.configureStimGroup(2, electrodes.lift(2).getOrElse(Nil))
    } yield ()
  }

  val configureElectrodes: Setting.ExperimentSettings => IO[Unit] = conf =>
    configureElectrodesManual(conf.stimulusElectrodes)


  /**
    Should always be set to manual
    */
  def setElectrodeMode(mode: String = "manual"): IO[Unit] =
    DspCalls.setElectrodeModes(mode)

  /**
    Creates a square wave with only high duty at 400 mV
    */
  def uploadSquareWaveform(duration: FiniteDuration, channel: Int): IO[Unit] = for {
    _ <- DspCalls.stopStimQueue
    _ <- WaveformGenerator.squareWave(channel, 0.milli, duration, 0.0, 400.0)
  } yield ()


  def stimuliRequestSink(allowed: List[Int] = List(0,1,2))(implicit ec: EC): Sink[IO, (Int,Option[FiniteDuration])] =
    DspCalls.stimuliRequestSink(allowed)

  def flashDSP: IO[Unit] = HttpClient.DSP.flashDsp

}
