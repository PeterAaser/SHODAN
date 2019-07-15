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

class DSP[F[_]: Sync](client: MEAMEHttpClient[F]) {

  val waveformGenerator = new cyborg.dsp.stimulus.WaveformGenerator(client)
  val dspCalls = new cyborg.dsp.calls.DspCalls(client, waveformGenerator)
  val dspSetup = new cyborg.dsp.calls.DspSetup(client, dspCalls)

  import calls._
  import client.DSP._

  /**
    Setup flashes the DSP, resets state, uploads a test wave, configures the electrodes
    specified by the settings, applies blanking and starts the stim queue.
    */
  def setup: Kleisli[F,FullSettings,Unit] = dspSetup.setup


  val stopStimQueue : F[Unit] = dspCalls.stopStimQueue
  val resetStimQueue : F[Unit] = dspCalls.resetStimQueue
  def setStimgroupPeriod(group: Int, period: FiniteDuration): F[Unit] =
    dspCalls.stimGroupChangePeriod(group, period)
  def enableStimGroup(group: Int): F[Unit] =
    dspCalls.enableStimReqGroup(group)
  def disableStimGroup(group: Int): F[Unit] =
    dspCalls.disableStimReqGroup(group)


  /**
    Creates stimulus requests for the supplied period, or toggles the group off when the period is None.
    For debugging convenience the three electrode groups can be toggled in the config
    */
  def stimuliRequestSink: Kleisli[Id,FullSettings,Pipe[F,StimReq,Unit] ] =
    dspSetup.stimuliRequestSink


  /**
    Should be called during setup, but important enough to be top level API.
    Underscored variation does no error checking and returns F[Unit]
    */
  def flashDSP_ = client.DSP.flashDsp
  def flashDSP: F[(Boolean, Boolean)] = dspSetup.flash


  /**
    Test if DSP works
    The placeholder is rather unlikely.
    */
  def getDspHealth: F[Boolean] = dspCalls.getDspHealth
}
