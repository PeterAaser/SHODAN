package com.cyborg

import com.cyborg.jetty.ApplicationServer
import fs2.{ Stream, Task }
import fs2._
import fs2.util.Async
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._


object Launcher {
  def main(args: Array[String]): Unit = {


    val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
    server.start()
    // mainLoop.outerT.unsafeRun()

    println("wello")

    // implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8)
    // implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(8)

    // val testStream = Stream.emits(List.fill(1000)(1)).repeat.covary[Task]
    // val layout = List(2,3,2)
    // val inputFilter = Assemblers.assembleInputFilter[Task]
    // val testRunner = GApipes.experimentPipe[Task](testStream.through(inputFilter), layout)

    // val uhh = testRunner
    //   // .through(_.map(λ => {println(λ); λ}))
    //   .run.unsafeRunFor(new FiniteDuration(20, SECONDS))
    // println(uhh)

  }
}
