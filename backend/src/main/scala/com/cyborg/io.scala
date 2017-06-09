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

  type ChannelStream[F[_],A]       = Stream[F,Vector[Stream[F,A]]]


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
    databaseIO.dbChannelStream(channels, experimentId)


  /**
    * Open a TCP connection to stream data from other other computer
    */
  def streamFromTCP(segmentLength: Int): Socket[Task] => ChannelStream[Task,Int] = {
    println("-------------- NYI -------------")
    ???
  }


  /**
    * Stream raw data from a TCP socket
    */
  // def streamFromTCPraw: Stream[Task,Int] =
  //   networkIO.socketStream[Task] flatMap ( socket =>
  //     {
  //       networkIO.rawDataStream(socket)
  //     })


  /**
    * Stream raw data from a TCP socket, send data back as well
    */
  // def streamFromTCPraw2(program: ((Stream[Task,Int], Sink[Task,Byte]) => Task[Unit])): Task[Unit] = {
  //   val disaster = networkIO.socketStream[Task] flatMap ( (socket: Socket[Task]) =>
  //     {
  //       val a = networkIO.rawDataStream(socket)
  //       Stream.eval(program(a, socket.writes()))
  //     })
  //   disaster.run
  // }


  /**
    * Creates database records and observes a stream, recording it to the database
    */
  def streamToDatabase: Task[Pipe[Task,Int,Int]] = ???


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

    def channelSinkZipper[F[_]: Async](channelStreams: Stream[F,Vector[Stream[F,Int]]], sinks: F[List[Sink[F,Int]]]): F[Unit] = {

      val writeTaskStream = channelStreams flatMap {
        channels: Vector[Stream[F,Int]] => {
          Stream.eval(sinks) flatMap {
            sinks: List[Sink[F,Int]] => {
              val a =
                ((channels zip sinks)
                   .map( { case (channel, sink) => channel.through(sink).drain } ))

              Stream.emits(a)
            }}}
      }

      concurrent.join(200)(writeTaskStream).drain.run
    }
  }
}
