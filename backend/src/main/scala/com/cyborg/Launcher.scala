package com.cyborg

import fs2._
import fs2.async.mutable.Topic
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import backendImplicits._

object Launcher {
  def main(args: Array[String]): Unit = {


    // import params.experiment._
    // staging.runFromHttp2(10000, List(3, 6, 9, 12)).unsafeRun()

    // mainRunner.httpRunner.unsafeRun()

    println("wello")

    println(mcsParser.getFiles.sorted)

    println("starting")
    val fug = mcsParser.gogo[Task]
    println(fug.through(_.map(_ => println("yo"))).run.unsafeRun())
    println("done")

    // val hurr = utilz.throttler(200.milliseconds).through(_.map{_ => println("ayy")})
    // val durr = Stream( 1 ).repeat.covary[Task].through(_.map{_ => println("lmao"); 1})

    // val derp: Stream[Task,Int] = hurr.zip(durr).map(_._2)

    // derp.runLog.unsafeRunFor(5.seconds)
    // val hurrdurr = Stream(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15)
    // hurrdurr.through(utilz.throttle(500.milliseconds)).through(_.map(println(_))).run.unsafeRun()
  }
}
