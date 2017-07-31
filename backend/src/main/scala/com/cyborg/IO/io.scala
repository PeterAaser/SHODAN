package com.cyborg

import fs2._
import fs2.Stream._
import fs2.async.mutable.Queue
import fs2.util.Async
import fs2.io.tcp._

import wallAvoid.Agent

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
    * For offline playback of data, select experiment id and a list of channels for playback
    */
  def streamFromDatabase(channels: List[Int], experimentId: Int): Stream[Task, Int] =
    databaseIO.dbChannelStream(experimentId)


  /**
    * Open a TCP connection to stream data from other other computer
    */
  def streamFromTCP(segmentLength: Int): Socket[Task] => ChannelStream[Task,Int] = {
    println("-------------- NYI -------------")
    ???
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
