package com.cyborg

import fs2.{ Stream, Task }
import fs2._
import fs2.util.Async
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._


object Launcher {
  def main(args: Array[String]): Unit = {

    // mainLoop.outerT.unsafeRun()
    // server.startServer.unsafeRun()
    staging.runFromHttp(1000, List(3, 6, 9, 12)).unsafeRun()

    println("wello")

  }
}
