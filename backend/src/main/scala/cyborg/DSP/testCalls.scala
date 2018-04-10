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

  def test: IO[Unit] = {
    for {
      _ <- waveformGenerator.squareWave(0, 0.4.second, 0.8.second, 0, 4000)
      _ <- IO { Thread.sleep(1000) }
      _ <- stimRequest(0, 3.second.toDSPticks, List(0))
      _ <- IO { Thread.sleep(1000) }
      _ <- startStimQueue
      _ <- IO { Thread.sleep(1000) }
      _ <- enableStimGroup(0)
      _ <- IO { Thread.sleep(10000) }
      _ <- stopStimQueue
      _ <- IO { Thread.sleep(1000) }
      _ <- DspLog.printDspLog
    } yield()
  }


  def stimulusTest1: IO[Unit] = {
    for {
      _ <- IO { say("Starting stimulus test baseline. No stimulus will be uploaded.") }
      _ <- IO { say("Electrode 0 should be stimulated with period 0.3 seconds") }
      _ <- IO { say("Amplitude should be 22.84mV") }
      _ <- IO { say("duration should be 60 milliseconds") }
      _ <- IO { Thread.sleep(100) }
      _ <- IO { say("requesting stimuli on electrode 0, period 0.3 seconds") }
      _ <- stimRequest(0, 0.3.second.toDSPticks, List(0))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("starting stim queue") }
      _ <- startStimQueue
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("enabling stim group 0...") }
      _ <- enableStimGroup(0)
      _ <- IO { say("Stimulus started...\n\n\n") }
    } yield ()
  }


  def stimulusTest2: IO[Unit] = {
    for {
      _ <- IO { say("Starting stimulus test 1. Aim is to replicate baseline") }
      _ <- IO { say("Electrode 0 should be stimulated with period 0.3 seconds") }
      _ <- IO { say("Amplitude should be 22.84mV") }
      _ <- IO { say("duration should be 60 milliseconds") }
      _ <- waveformGenerator.sineWave(0, 60.milliseconds, 22.84)
      _ <- IO { Thread.sleep(100) }
      _ <- IO { say("requesting stimuli on electrode 0, period 0.3 seconds") }
      _ <- stimRequest(0, 0.3.second.toDSPticks, List(0))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("starting stim queue") }
      _ <- startStimQueue
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("enabling stim group 0...") }
      _ <- enableStimGroup(0)
      _ <- IO { say("Stimulus started...\n\n\n") }
    } yield ()
  }


  def stimulusTest3: IO[Unit] = {
    for {
      _ <- IO { say("Starting stimulus test 2. Aim is to test square wave generation.") }
      _ <- IO { say("Electrode 0 should be stimulated with period 0.3 seconds") }
      _ <- IO { say("Amplitude should be 100mV") }
      _ <- IO { say("duration should be 100 milliseconds") }
      _ <- waveformGenerator.squareWave(0, 0.1.second, 0.1.second, 0, 100)
      _ <- IO { Thread.sleep(100) }
      _ <- IO { say("requesting stimuli on electrode 0, period 0.3 seconds") }
      _ <- stimRequest(0, 0.3.second.toDSPticks, List(0))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("starting stim queue") }
      _ <- startStimQueue
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("enabling stim group 0...") }
      _ <- enableStimGroup(0)
      _ <- IO { say("Stimulus started...\n\n\n") }
    } yield ()
  }


  def oscilloscopeTest1: IO[Unit] = {
    for {
      _ <- IO { say("Uploading square wave, duration 0.1 sec, amplitude 400mV") }
      _ <- waveformGenerator.squareWave(0, 0.1.second, 0.1.second, 0, 400)
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("requesting stimuli on electrode 0, period 0.3 seconds") }
      _ <- stimRequest(0, 0.3.second.toDSPticks, List(0))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("starting stim queue") }
      _ <- startStimQueue
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("enabling stim group 0...") }
      _ <- enableStimGroup(0)
      _ <- IO { say("Stimulus started...\n\n\n") }
    } yield ()
  }


  def oscilloscopeTest2: IO[Unit] = {
    for {
      _ <- IO { say("Uploading square wave, duration 0.1 sec, amplitude 400mV") }
      _ <- waveformGenerator.squareWave(0, 0.1.second, 0.1.second, 0, 400)
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("requesting stimuli on electrode 0, period 0.3 seconds, stim group 0") }
      _ <- stimRequest(0, 0.3.second.toDSPticks, List(0))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("requesting stimuli on electrode 3,4 and 5, period 0.5 seconds, stim group 1") }
      _ <- stimRequest(0, 0.5.second.toDSPticks, List(3,4,5))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("starting stim queue") }
      _ <- startStimQueue
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("enabling stim group 0 and 1...") }
      _ <- enableStimGroup(0)
      _ <- enableStimGroup(1)
      _ <- IO { say("Stimulus started...\n\n\n") }
    } yield ()
  }


  def oscilloscopeTest3: IO[Unit] = {
    for {
      _ <- IO { say("Uploading square wave, duration 0.1 sec, amplitude 400mV, offset -200mV") }
      _ <- waveformGenerator.squareWave(0, 0.05.second, 0.1.second, -200, 400)
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("requesting stimuli on electrode 0, period 0.3 seconds, stim group 0") }
      _ <- stimRequest(0, 0.3.second.toDSPticks, List(0))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("requesting stimuli on electrode 3,4 and 5, period 0.5 seconds, stim group 1") }
      _ <- stimRequest(1, 0.5.second.toDSPticks, List(3,4,5))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("requesting stimuli on electrode 10 and 12, period 0.2 seconds, stim group 2") }
      _ <- stimRequest(2, 0.2.second.toDSPticks, List(10,12))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("requesting stimuli on electrode 11 and 13, period 0.15 seconds, stim group 3") }
      _ <- stimRequest(3, 0.15.second.toDSPticks, List(11,13))
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("starting stim queue") }
      _ <- startStimQueue
      _ <- IO { Thread.sleep(200) }
      _ <- IO { say("enabling all stim groups") }
      _ <- enableStimGroup(0)
      _ <- enableStimGroup(1)
      _ <- enableStimGroup(2)
      _ <- enableStimGroup(3)
      _ <- IO { say("Stimulus started...\n\n\n") }
    } yield ()
  }
}
