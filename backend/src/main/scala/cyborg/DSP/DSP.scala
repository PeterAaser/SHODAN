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
  def setup(blanking: Boolean = true,
            blankingProtection: Boolean = true): Setting.ExperimentSettings => IO[Unit] =
    DspCalls.setup(blanking, blankingProtection)

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

    Enables electrodes, configures source DAC and sets stimulating
    electrodes to auto mode.
    Please don't ask questions about what the fuck auto mode does,
    you'll never be able to understand the brilliant 5D chess of the
    electro-engineers anyways.
    */
  def configureElectrodesManual(electrodes: List[List[Int]]): IO[Unit] = {
    for {
      _ <- DspCalls.resetStimQueue
      _ <- DspCalls.configureStimGroup(0, electrodes.lift(0).getOrElse(Nil))
      _ <- DspCalls.configureStimGroup(1, electrodes.lift(1).getOrElse(Nil))
      _ <- DspCalls.configureStimGroup(2, electrodes.lift(2).getOrElse(Nil))
      _ <- DspCalls.setElectrodeModes(electrodes.flatten)
    } yield ()
  }

  val configureElectrodes: Setting.ExperimentSettings => IO[Unit] = conf =>
    configureElectrodesManual(conf.stimulusElectrodes)


  /**
    Should always be set to manual
    */
  def setElectrodeMode(electrodes: List[Int]): IO[Unit] =
    DspCalls.setElectrodeModes(electrodes)

  /**
    Creates a square wave with only high duty at 400 mV
    */
  def uploadSquareWaveform(duration: FiniteDuration, channel: Int): IO[Unit] = for {
    _ <- DspCalls.stopStimQueue
    _ <- WaveformGenerator.squareWave(channel, 0.milli, duration, 0.0, 400.0)
  } yield ()


  /**
    Creates stimulus requests for the supplied period, or toggles the group off when the period is None.
    For debugging convenience the three electrode groups can be toggled by supplying a list of allowed groups.
    */
  def stimuliRequestSink(toggledGroups: List[Int] = List(0,1,2))(implicit ec: EC): Sink[IO, (Int,Option[FiniteDuration])] =
    DspCalls.stimuliRequestSink(toggledGroups)

  def flashDSP: IO[Unit] = HttpClient.DSP.flashDsp
}
