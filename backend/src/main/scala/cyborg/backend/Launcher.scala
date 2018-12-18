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

    Assemblers.startSHODAN.compile.drain.unsafeRunSync()
  }
}
