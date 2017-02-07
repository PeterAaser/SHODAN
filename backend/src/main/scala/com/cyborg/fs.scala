package SHODAN

import scala.concurrent.duration._

import fs2._
import fs2.util.Async
import fs2.io.file._
import java.nio.file._
import simulacrum._

import shapeless._
import ops.tuple.FlatMapper
import syntax.std.tuple._


object Launcher {
  def main(args: Array[String] ): Unit = {
    val startTime = System.nanoTime

    val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
    server.start()

    import scala.concurrent.duration._
    val duration: Double = (System.nanoTime - startTime).nanos.toUnit(SECONDS)
    println(s"Application started in ${duration}s.")
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
