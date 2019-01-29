package cyborg.dsp
import cats.data._
import cats.implicits._
import cats._
import cyborg._

import fs2._
import cats.effect._
import scala.concurrent.duration._
import cyborg.utilz._
import cyborg.Settings._

import stimulus.WaveformGenerator

class DSP[F[_]](client: MEAMEHttpClient[F]) {

  val waveformGenerator = new cyborg.dsp.stimulus.WaveformGenerator(client)
  val dspCalls = new cyborg.dsp.calls.DspCalls(client, waveformGenerator)
  val dspSetup = new cyborg.dsp.calls.DspSetup(client, dspCalls)

  import calls._
  import client.DSP._

  /**
    Setup flashes the DSP, resets state, uploads a test wave, configures the electrodes
    specified by the settings, applies blanking and starts the stim queue.
    */
  def setup: Kleisli[IO,FullSettings,Unit] = dspSetup.setup


  val stopStimQueue : IO[Unit] = dspCalls.stopStimQueue
  val resetStimQueue : IO[Unit] = dspCalls.resetStimQueue
  def setStimgroupPeriod(group: Int, period: FiniteDuration): IO[Unit] =
    dspCalls.stimGroupChangePeriod(group, period)
  def enableStimGroup(group: Int): IO[Unit] =
    dspCalls.enableStimReqGroup(group)
  def disableStimGroup(group: Int): IO[Unit] =
    dspCalls.disableStimReqGroup(group)


  /**
    Creates a square wave with only high duty at 400 mV

    setup handles this, so you do not need to manually call this function.
    Not sure if it deserves to be in the top level API...
    */
  def uploadSquareWaveform(duration: FiniteDuration, channel: Int): IO[Unit] = for {
    _ <- dspCalls.stopStimQueue
    _ <- waveformGenerator.squareWave(channel, 0.milli, duration, 0.0, 400.0)
  } yield ()


  /**
    Creates stimulus requests for the supplied period, or toggles the group off when the period is None.
    For debugging convenience the three electrode groups can be toggled in the config
    */
  def stimuliRequestSink: Kleisli[Id,FullSettings,Sink[IO,(Int,Option[FiniteDuration])] ] =
    dspSetup.stimuliRequestSink


  /**
    Should be called during setup, but important enough to be top level API
    */
  def flashDSP: IO[Unit] = client.DSP.flashDsp
}
