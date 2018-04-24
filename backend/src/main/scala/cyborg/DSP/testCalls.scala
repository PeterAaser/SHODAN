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


object DspTests {
  import DspCalls._


  def uploadSquareTest(period: FiniteDuration, amplitude: Int): IO[Unit] = for {
    _ <- IO { say(s"Uploading square wave. period: $period, amplitude: ${amplitude}mV") }
    _ <- waveformGenerator.squareWave(0, (period/2), (period/2), 0, amplitude)
  } yield ()


  def uploadSineTest(period: FiniteDuration, amplitude: Int): IO[Unit] = for {
    _ <- IO { say(s"Uploading square wave. period: $period, amplitude: ${amplitude}mV") }
    _ <- waveformGenerator.sineWave(0, period, amplitude)
  } yield ()


  def makeTestWithElectrodes(electrodes: List[Int]): IO[Unit] = for {
    _ <- IO { say(s"Starting stim test with user input: ${electrodes}") }
    _ <- IO { say("requesting stimuli") }
    _ <- stimRequest(0, 300.milliseconds.toDSPticks, electrodes)
    _ <- IO { Thread.sleep(200) }
    _ <- IO { say("starting stim queue") }
    _ <- startStimQueue
    _ <- IO { Thread.sleep(200) }
    _ <- IO { say("enabling stim group 0...") }
    _ <- enableStimGroup(0)
  } yield ()


}
