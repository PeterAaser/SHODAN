package com.cyborg

import fs2._
import fs2.async.mutable.Topic
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import backendImplicits._


import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import java.io.File
import java.nio.file.Paths

object Launcher {
  def main(args: Array[String]): Unit = {

    println("wello")

    Assemblers.assembleMcsFileReader.run.unsafeRunSync()
  }
}
