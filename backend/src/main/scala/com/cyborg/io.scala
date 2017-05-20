package com.cyborg

import com.cyborg.params._
import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.io.tcp._

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import java.net.InetSocketAddress

import scala.language.higherKinds
import com.typesafe.config._


object IO {

  // implicit val tcpACG : AsynchronousChannelGroup = namedACG("tcp")
  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(8)

  type ChannelStream[F[_],A] = Stream[F,Vector[Stream[F,A]]]

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


  // TODO move implementation details
  def streamFromTCPreadOnly(segmentLength: Int): Stream[Task,Int] =
    networkIO.socketStream[Task] flatMap ( socket =>
      {
        networkIO.rawDataStream(socket)
      })


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
    utilz.alternator(rawStream, 1024, 60)
  }


  /**
    * Reads a flat file from MEAME
    */
  def meameDataReader(filename: String): ChannelStream[Task,Int] = {
    val rawStream = fileIO.meameDataReader[Task](filename)
    utilz.alternator(rawStream, 1024, 60)
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
