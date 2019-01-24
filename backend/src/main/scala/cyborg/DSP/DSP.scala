package cyborg.dsp
import cats.data.Kleisli
import cyborg._

import fs2._
import cats.effect._
import scala.concurrent.duration._
import cyborg.utilz._

import stimulus.WaveformGenerator

object DSP {

  import calls._
  import HttpClient.DSP._

  /**
    Setup flashes the DSP, resets state, uploads a test wave, configures the electrodes
    specified by the settings, applies blanking and starts the stim queue.
    */
  def setup: ConfF[IO,Unit] = DspSetup.setup


  val stopStimQueue : IO[Unit] = DspCalls.stopStimQueue
  val resetStimQueue : IO[Unit] = DspCalls.resetStimQueue
  def setStimgroupPeriod(group: Int, period: FiniteDuration): IO[Unit] =
    DspCalls.stimGroupChangePeriod(group, period)
  def enableStimGroup(group: Int): IO[Unit] =
    DspCalls.enableStimReqGroup(group)
  def disableStimGroup(group: Int): IO[Unit] =
    DspCalls.disableStimReqGroup(group)


  /**
    Creates a square wave with only high duty at 400 mV

    setup handles this, so you do not need to manually call this function.
    Not sure if it deserves to be in the top level API...
    */
  def uploadSquareWaveform(duration: FiniteDuration, channel: Int): IO[Unit] = for {
    _ <- DspCalls.stopStimQueue
    _ <- WaveformGenerator.squareWave(channel, 0.milli, duration, 0.0, 400.0)
  } yield ()


  /**
    Creates stimulus requests for the supplied period, or toggles the group off when the period is None.
    For debugging convenience the three electrode groups can be toggled in the config
    */
  def stimuliRequestSink: ConfId[ Sink[IO,(Int,Option[FiniteDuration])] ] =
    calls.DspSetup.stimuliRequestSink


  /**
    Should be called during setup, but important enough to be top level API
    */
  def flashDSP: IO[Unit] = HttpClient.DSP.flashDsp
}
