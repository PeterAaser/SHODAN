package com.cyborg

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import fs2.async.mutable.Queue
import fs2.concurrent

import fs2.util.Async
import java.io.File
import java.nio.file.Paths
import fs2.io.tcp._
import fs2._
import params._


/**
  Takes in a filepath, performs the magic.
  */
object mcsParser {

  def getFiles = fileIO.getListOfFiles("/home/peteraa/datateknikk/hdf5_stuff/meme_storage")


  def insertRecordFile(inData: Stream[Task,Byte], channelRecordingId: Long, channel: Int): Task[Unit] = {
    val dataStream = inData
      .through(text.utf8Decode)
      .through(text.lines)
      .through(_.map(_.split(",").map(_.toInt).toList))
      .through(utilz.chunkify)

    val channelSink = databaseIO.dbWriters.createChannelSink(channel, channelRecordingId: Long)
    dataStream.through(channelSink).run
  }

  def doTheThing = {

    val createExperimentTask = databaseIO.dbWriters.createExperiment(Some("Experiment fetched from hfd5"))

    ???
  }

  def readFile: Task[Vector[Int]] = {
    io.file.readAll[Task](Paths.get("/home/peteraa/MEAMEdata/test/meme.msrd"), 4096)
      .through(utilz.bytesToInts)
      .runLog
  }



}
