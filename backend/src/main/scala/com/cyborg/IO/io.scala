package com.cyborg

import fs2._
import fs2.Stream._
import fs2.async.mutable.Queue
import fs2.util.Async
import fs2.io.tcp._

import wallAvoid.Agent
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import utilz._

import scala.language.higherKinds


object IO {

  import backendImplicits._

  type ChannelStream[F[_],A]     = Stream[F,Vector[Stream[F,A]]]

  // TODO these should be Option
  // then every function can take them as args and check if they exist
  type FullStreamHandler[F[_]]   = Sink[F,Byte]
  type SelectStreamHandler[F[_]] = Sink[F,Byte]
  type FeedbackStream[F[_]]      = Stream[F,Byte]

  import params.experiment._


  /**
    * For offline playback of data, select experiment id to publish on provided topics
    */
  def streamFromDatabase(experimentId: Int, topics: Stream[Task,dbDataTopic[Task]]): Stream[Task, Unit] = {
    for {
      experimentInfo <- databaseIO.getExperimentSampleRate(experimentId) // samplerate, not use atm
      experimentData = databaseIO.dbChannelStream(experimentId)
    } yield {
      Assemblers.broadcastDataStream(experimentData, topics)
    }
  }


  /**
    * Open a TCP connection to stream data from other computer
    * Data is broadcasted to provided topics
    */
  def streamFromTCP(topics: Stream[Task,meameDataTopic[Task]]): Stream[Task, Unit] = {
    val experimentData = networkIO.streamAllChannels[Task]
    Assemblers.broadcastDataStream(experimentData, topics)
  }


  /**
    * Reads a dump file from MEAME
    */
  // TODO move implementation details
  def meameDumpReader: ChannelStream[Task,Int] = {
    val rawStream = fileIO.meameDumpReader[Task]
    utilz.alternator(rawStream, 1024, 60, 1024)
  }


  /**
    * Reads a flat file from MEAME
    */
  def meameDataReader(filename: String): ChannelStream[Task,Int] = {
    val rawStream = fileIO.meameDataReader[Task](filename)
    utilz.alternator(rawStream, 1024, 60, 1024)
  }


  object IOmethods {

    def dbRead(exepirmentId: Int): Stream[Task, Int] = {

      ???
    }

  }


  object IOactions {
  }
}
