package com.cyborg

import cats.effect.IO

// import java.io.File
import java.nio.file.Paths
import fs2._


/**
  Takes in a filepath, performs the magic.
  */
object mcsParser {

  def getFiles = fileIO.getListOfFiles("/home/peteraa/datateknikk/hdf5_stuff/meme_storage")



  def insertRecordFile(inData: Stream[IO,Byte], channelRecordingId: Long, channel: Int): IO[Unit] = {
    val dataStream = inData
      .through(text.utf8Decode)
      .through(text.lines)
      .through(_.map(_.split(",").map(_.toInt).toList))
      .through(utilz.chunkify)

    val channelSink = databaseIO.dbWriters.createChannelSink(channel, channelRecordingId: Long)
    dataStream.through(channelSink).run
  }

  def doTheThing = {

    // val createExperimentTask = databaseIO.dbWriters.createExperiment(Some("Experiment fetched from hfd5"))

    ???
  }

  def readFile: IO[Vector[Int]] = {
    io.file.readAll[IO](Paths.get("/home/peteraa/MEAMEdata/test/meme.msrd"), 4096)
      .through(utilz.bytesToInts)
      .runLog
  }



}
