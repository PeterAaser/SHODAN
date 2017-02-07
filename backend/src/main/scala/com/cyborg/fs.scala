package com.cyborg

import scala.concurrent.duration._

import fs2._
import fs2.util.Async
import fs2.io.file._
import java.nio.file._
import simulacrum._

import shapeless._
import ops.tuple.FlatMapper
import syntax.std.tuple._


// object Launcher {
//   def main(args: Array[String] ): Unit = {
//     val startTime = System.nanoTime
//
//     val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
//     server.start()
//
//     import scala.concurrent.duration._
//     val duration: Double = (System.nanoTime - startTime).nanos.toUnit(SECONDS)
//     println(s"Application started in ${duration}s.")
//   }
// }
