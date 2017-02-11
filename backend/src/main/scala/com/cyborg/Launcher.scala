package com.cyborg

import com.cyborg.jetty.ApplicationServer

import fs2._

object Launcher {
  def main(args: Array[String]): Unit = {
    val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
    server.start()
    println("Good meme!")
  }
}

// object FsMain {
//   def main(args: Array[String]) : Unit = {
//     println("nice meme")
//
//   // import Assemblers._
//
//     // BooleanNetwork.rbnTest
//
//     implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")
//     implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(8)
//
//
//     val ip = "129.241.111.251"
//     val port = 1255
//
//     val reuseAddress = true
//     val sendBufferSize = 256*1024
//     val receiveBufferSize = 256*1024
//     val keepAlive = true
//     val noDelay = true
//
//
//     val crash = neuroServer.runServer[Task]
//
//     val explode = crash.unsafeRun
//   }
// }
