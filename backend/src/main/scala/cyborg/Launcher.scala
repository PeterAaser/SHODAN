package cyborg

import fs2._
import fs2.async.mutable.Topic
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
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

    params.printParams()

    // Assemblers.startSHODAN.run.unsafeRunSync()
    val meme = databaseIO.dbReaders.dbChannelStream(2).take(10000).runLog.unsafeRunSync()
    println(meme)

  }
}
