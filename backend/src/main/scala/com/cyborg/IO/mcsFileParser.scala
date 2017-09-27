package com.cyborg

import cats.effect.IO
import java.io.File
import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import java.nio.file.Path
import scala.concurrent.ExecutionContext

// import java.io.File
import java.nio.file.Paths
import fs2._
import doobieTasks._


/**
  Takes in a filepath, performs the magic.
  */
object mcsParser {

  case class recordingInfo(comment: String, MEA: Int){
    def makeString = Some("Info string generated from recordingInfo")
  }


  // It's ugly as sin. Too bad
  def getInfo(infoPath: Path): Stream[IO,recordingInfo] = {

    val s: Stream[IO,String] = fs2.io.file.readAll[IO](infoPath, 4096)
      .through(text.utf8Decode)
      .through(text.lines)

    def theThing: Pipe[IO,String,recordingInfo] = {
      def go(s: Stream[IO,String]): Pull[IO,recordingInfo,Unit] = {
        s.pull.unconsN(10, true).flatMap {
          case Some((strangz, tl)) => {
            var MEA: Int = -1
            var comment = ""
            val info = strangz.toVector
            if(info(0) contains "MEA"){
              MEA = info(0).split(":")(1).toInt
              comment = info(1)
            }
            else{
              MEA = -1
              comment = info(0)
            }

            Pull.output1(recordingInfo(comment, MEA))
          }
          case None => {
            println("WEEEE WOOO WEEEE WOOO SOMETHING IS WRONG!!")
            Pull.done
          }
        }
      }
      in => go(in).stream
    }
    s.through(theThing)
  }


  def processRecording(folderPath: Path)(implicit ec: ExecutionContext): Stream[IO,Unit] = {

    val files = fileIO.getListOfFiles(folderPath)
    val infoFile = files.filter(file => "test.txt".equals(file.getName)).head
    val infoStream = getInfo(infoFile.toPath())
    val dataFiles = files.filter(file => !("info.txt".equals(file.getName))).sortBy(_.getName)
    val date = parseFolderDate(folderPath)

    infoStream flatMap{
      info => {
        Stream.eval(databaseIO.dbWriters.createOldExperiment(info.makeString, date)) flatMap {
          experimentId => {

            // channel recording id's for channel 0 - 59
            val channelRecordings = doobieTasks.doobieQueries.getChannels(experimentId)

            def pairToSink(channelRecording: ChannelRecording,  channel: Long): Sink[IO,Int] = {
              import databaseIO.dbWriters._
              createChannelSink(channel.toInt, channelRecording.channelRecordingId)
            }

            // One sink for each chonnel
            val channelRecordingSinks: Stream[IO,Sink[IO,Int]] = channelRecordings
              .zipWithIndex
              .through(_.map((pairToSink _).tupled))

            val dataFileStreamsList = dataFiles.map(λ => fileIO.streamChannelData(λ.getPath))

            // A stream of streams, one for each file input
            val dataFileStreams = Stream.emits(dataFileStreamsList).covary[IO]

            // a stream of streams where each inner stream inserts a file
            // dataFileStreams zip channelRecordingSinks map(λ => λ._1.through(λ._2))
            val hurr = dataFileStreams zip channelRecordingSinks map(λ => λ._1.through(λ._2))

            val durr = hurr.join(10)

            durr
          }
        }
      }
    }
  }

  def isRecordingDirectory(dir: Path): Boolean = {
    val files = fileIO.getListOfFiles(dir)
    val folders = fileIO.getListOfFolders(dir)

    // No subdirs, an info file and the channel files. Bery gud encod :DDD
    val filesValid = files.size == 61
    val foldersValid = folders.size == 0
    filesValid && foldersValid
  }

 def eatDirectory(dir: Path)(implicit ec: ExecutionContext): Stream[IO, Unit] = {
   println(s"Eating directiory $dir")
    if(isRecordingDirectory(dir)){
      println(s"\tProcessing folder")
      processRecording(dir)
    }
    else {
      println("\tprocessing subdirs")
      val subFolders = fileIO.getListOfFolders(dir)
      val subStreams = subFolders.map(λ => eatDirectory(λ.toPath()))
      Stream.emits(subStreams).covary[IO].flatMap(identity)
    }
  }

  def parseFolderDate(folderPath: Path): DateTime = {
    val pyFormat = DateTimeFormat.forPattern("YYYY-MM-DD_HH-mm-ss")
    println(folderPath)
    println(folderPath.getFileName)
    println(folderPath.getFileName.toString())
    DateTime.parse(folderPath.getFileName.toString(), pyFormat)
  }
}
