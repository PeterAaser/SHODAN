package com.cyborg

import fs2._
import fs2.async.mutable.Topic
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import backendImplicits._

import cats.effect._
import utilz._

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import java.io.File
import java.nio.file.Paths

import backendImplicits._

object Launcher {
  def main(args: Array[String]): Unit = {

    println("wello")

    Assemblers.startSHODAN.run.unsafeRunSync()
  }
}


object SAtest {
  def gogo(): Unit = {
    val stream = Stream(1,2,3).repeat.covary[IO]
    val topics = utilz.createTopics[IO,Vector[Int]](2, Vector(-1))

    val urp = topics flatMap {
      topics => {
        val huh = saDebug.broadcastDataStream(stream, topics)
        huh.concurrently(topics(0).subscribe(10).through(_.map(println))
                           .concurrently(topics(1).subscribe(10).through(_.map(println))))
      }
    }

    urp.run.unsafeRunTimed(5.second)
  }
}

object DBtest {
  def gogo(): Unit = {

    val topics = utilz.createTopics[IO, DataSegment](60, (Vector[Int](), -1))
    val stream = Stream(1,2,3).repeat.covary[IO]

    val urp = topics flatMap {
      topics => {
        val huh = Assemblers.broadcastDataStream(stream, topics)
        val spem = topics(0).subscribe(10).through(_.map(println))
        huh.concurrently(spem)
      }
    }

    urp.run.unsafeRunTimed(5.second)

  }
}

object DBtest2 {
  def gogo(): Unit = {

    val topics = utilz.createTopics[IO, DataSegment](60, (Vector[Int](), -1))
    val stream = dIO.streamFromDatabaseRaw(1)
    // val stream = Stream((Vector(1,2,3),0), (Vector(1,2,3),1), (Vector(1,2,3),2)).repeat.covary[IO]

    val urp = topics flatMap {
      topics => {
        val huh = Assemblers.broadcastDataStream(stream, topics)
        val spem = topics(0).subscribe(10).through(_.map(println))
        huh.concurrently(spem)
      }
    }

    urp.run.unsafeRunTimed(50.second)
  }
}
