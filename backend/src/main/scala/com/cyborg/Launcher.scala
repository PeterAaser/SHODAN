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

    mainRunner.httpRunner.unsafeRun()

    println("wello")
    // val hurr = utilz.throttler(200.milliseconds).through(_.map{_ => println("ayy")})
    // val durr = Stream( 1 ).repeat.covary[Task].through(_.map{_ => println("lmao"); 1})

    // val derp: Stream[Task,Int] = hurr.zip(durr).map(_._2)

    // derp.runLog.unsafeRunFor(5.seconds)
    // val hurrdurr = Stream(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15)
    // hurrdurr.through(utilz.throttle(500.milliseconds)).through(_.map(println(_))).run.unsafeRun()


  //   val someTopics = utilz.createTopics[Task,Int](3, -1)
  //   val hrmm = someTopics flatMap {
  //     topics => {
  //       val out = topics.map(_.subscribe(5))
  //       val in: List[Stream[Task,Unit]] = topics.map(topic => Stream(1,2,3).repeat.through(topic.publish))

  //       val inStream = in.foldLeft( Stream[Task,Unit](()))( (λ, µ) => µ.merge(λ) )
  //       val outStream = out.foldLeft( Stream(0).covary[Task])( (λ, µ) => λ.merge(µ) )
  //       inStream.mergeDrainL(outStream)
  //     }
  //   }
  //   println(hrmm.through(pipe.take(20)).runLog.unsafeRun())



  //   val myTopicTask = fs2.async.topic[Task, Int](-1)
  //   val niceMeme = Stream.eval(myTopicTask) flatMap { topic =>
  //     val huh = Stream(1, 2, 3, 4, 5).repeat.through(topic.publish)
  //     val what = topic.subscribe(20)
  //     huh.mergeDrainL(what)
  //   }

  //   println(niceMeme.through(pipe.take(10)).runLog.unsafeRun())


  //   val myTopicsTasks: Stream[Task, List[Task[Topic[Task,Int]]]] = Stream( List(
  //                                                                           fs2.async.topic[Task, Int](-1),
  //                                                                           fs2.async.topic[Task, Int](-1)
  //                                                                         ))

  //   val myTopics = myTopicsTasks flatMap {
  //     topicList: List[Task[Topic[Task, Int]]] => {

  //       def helper(as: Stream[Task,Topic[Task,Int]], bs: List[Task[Topic[Task,Int]]]): Stream[Task,Topic[Task,Int]] = {
  //         bs match {
  //           case h :: t => {
  //             val a = Stream.eval(h) ++ as
  //             helper(a, t)
  //           }
  //           case _ => as
  //         }
  //       }
  //       helper(Stream.empty, topicList).through(utilz.vectorize(2))
  //     }
  //   }

  //   val hbt = myTopics flatMap {
  //     topics => {
  //       val ins = Stream(1, 2, 3).repeat.through(topics.head.publish)
  //       val outs = topics.head.subscribe(20)
  //       ins.mergeDrainL(outs)
  //     }
  //   }
  //   println(hbt.through(pipe.take(20)).runLog.unsafeRun())
  }
}
