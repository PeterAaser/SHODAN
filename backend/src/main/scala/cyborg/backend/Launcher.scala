package cyborg.backend

import fs2.concurrent.Broadcast
import scala.concurrent.duration._

import cyborg._
import utilz._
import cats.effect._
import fs2._
import backendImplicits._

import cyborg.dsp.calls.DspCalls._


object Launcher {
  def main(args: Array[String]): Unit = {

    say("wello")
    // val hurr = for {
    //   _ <- HttpClient.flashDsp
    //   _ <- DspCalls.defaultSetup
    //   log <- DspLog.getDspLog
    //   _ <- IO { say(log) }
    //   _ <- IO { say("sleeping for 10 secs") }
    //   _ <- DspCalls.readStimQueueState
    //   _ <- DspCalls.readDebug
    //   _ <- IO.sleep(10.second)
    //   _ <- DspCalls.checkShotsFired
    //   _ <- DspCalls.checkForErrors
    //   _ <- DspCalls.checkSteps
    //   _ <- DspCalls.readStimQueueState
    //   _ <- DspCalls.stimGroupChangePeriod(0, 1000.millis.toDSPticks)
    // } yield ()
    // hurr.unsafeRunSync()

    Assemblers.startSHODAN.compile.drain.unsafeRunSync()
  }
}
