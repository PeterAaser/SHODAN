package cyborg
import cats.effect.IO
import utilz._
import fs2._
import fs2.async._
import cats.implicits._
import cats.syntax._

import scala.concurrent.duration._

import HttpClient._

object dspStimTest {

  // val stim1 = StimGroupRequest(0, (0 until 20).toList, 5000)
  // val stim2 = StimGroupRequest(1, List(22, 24, 26, 28, 30), 8000)
  // val stim3 = StimGroupRequest(2, (40 until 50).toList, 2000)
  // val stim4 = StimGroupRequest(3, (51 until 60).toList, 1000)

  val call1 = dspCall(1,
                          0x104 -> 0x108,
                          0x200 -> 0x204)

  val call2 = dspCall(2,
                          0x104 -> 0x200,
                          0x108 -> 0x204,
                          0x10c -> 0x20c)

  def funcCallTest: IO[Unit] = {

    for {
      _ <- call1
      _ <- call2
    } yield()
  }

  def squareWaveUploadTest: IO[Unit] = {
    for {

      _ <- IO { say("Uploading square waves to channel 0") }
      _ <- waveformGenerator.squareWave(0, 0.2.second, 0.4.second, 0, 100)

    } yield ()
  }


  def requestTest: IO[Unit] = {
    for {
      _ <- IO { say("stimulus with period 0.1s applied to electrodes 0 until 20 for group 0"); Thread.sleep(5000) }
      // _ <- stimGroupRequest(stim1)
    } yield ()
  }


  def sineWaveTest: IO[Unit] = {
    val theTask = for {
      // _ <- stimGroupRequest(stim1)
      _ <- IO { say("stimulus with period 0.1s applied to electrodes 0 until 20 for group 0"); Thread.sleep(5000) }
      // _ <- stimGroupRequest(stim2)
      _ <- IO { say("stimulus with period 0.16s applied to electrodes 0 until 20 for group 0"); Thread.sleep(5000) }
      // _ <- stimGroupRequest(stim3)
      _ <- IO { say("stimulus with period 0.04s applied to electrodes 0 until 20 for group 0"); Thread.sleep(5000) }
      // _ <- stimGroupRequest(stim4)
      _ <- IO { say("stimulus with period 0.02s applied to electrodes 0 until 20 for group 0"); Thread.sleep(5000) }
    } yield ()

    theTask.replicateA(5).void
  }

}
