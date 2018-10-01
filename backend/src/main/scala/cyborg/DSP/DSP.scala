package cyborg

import fs2._
import cats._
import cats.syntax._
import cats.implicits._
import cats.effect.implicits._
import cats.effect._
import scala.concurrent.duration._


object DSP {

  case class mV(mv: Double)

  /**
    Configures stimulus with a square waveform and only group 0 active
    */
  val defaultConfig:  IO[Unit] = DspCalls.defaultSetup
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
  def configureElectrodes(electrodes: List[List[Int]]): IO[Unit] = {
    for {
      _ <- DspCalls.resetStimQueue
      _ <- DspCalls.configureStimGroup(0, electrodes.lift(0).getOrElse(Nil))
      _ <- DspCalls.configureStimGroup(1, electrodes.lift(1).getOrElse(Nil))
      _ <- DspCalls.configureStimGroup(2, electrodes.lift(2).getOrElse(Nil))
    } yield ()
  }


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
}