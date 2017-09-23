package com.cyborg

import cats.effect.IO
import java.io.File

// import java.io.File
import java.nio.file.Paths
import fs2._


/**
  Takes in a filepath, performs the magic.
  */
object mcsParser {

  case class recordingInfo(date: String, comment: String){
    def makeString = Some("Info string generated from recordingInfo")
  }

  def getInfo(infoPath: String): Stream[IO,recordingInfo] = {???}

  def processRecording(folderPath: String): Stream[IO,Stream[IO,Unit]] = {

    val files = fileIO.getListOfFiles(folderPath)
    val infoFile = files.filter(file => "info.txt".equals(file.getName)).head
    val infoStream = getInfo(infoFile.getName)
    val dataFiles = files.filter(file => !("info.txt".equals(file.getName))).sortBy(_.getName)

    infoStream flatMap{
      info => {
        Stream.eval(databaseIO.dbWriters.createExperiment(info.makeString)) flatMap {
          experimentId => {
            val channelRecordings = doobieTasks.doobieQueries.getChannels(experimentId)
            val channelRecordingSinks = channelRecordings.zipWithIndex.through(_.map(λ => databaseIO.dbWriters.createChannelSink(λ._2.toInt, λ._1.channelRecordingId)))
            val dataFileStreams = Stream.emits(dataFiles.map(λ => fileIO.streamChannelData(λ.getPath))).covary[IO]

            dataFileStreams zip channelRecordingSinks map(λ => λ._1.through(λ._2))
          }
        }
      }
    }
  }

  def isRecordingDirectory(dir: String): Boolean = {
    val files = fileIO.getListOfFiles(dir)
    val folders = fileIO.getListOfFolders(dir)

    // No subdirs, an info file and the channel files. Bery gud encod :DDD
    val filesValid = files.size == 61
    val foldersValid = folders.size == 0
    filesValid && foldersValid
  }

  def eatDirectory(dir: String): Stream[IO, Unit] = {


    ???
  }
}
