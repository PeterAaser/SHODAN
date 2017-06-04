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

  // hardcoded
  val samplerate = 40000

  def runFromHttp(
    segmentLength: Int,
    selectChannels: List[Int]
  ): Task[Unit] = {

    // hardcoded

    val commandQueueTask: Task[Queue[Task,Int]] = fs2.async.unboundedQueue[Task,Int]

    val doThing = Stream.eval(commandQueueTask) flatMap { commandQueue =>
      {

        val httpServerTask: Task[Unit] = httpServer.startServer(commandQueue.enqueue)

        val configAndStartMEAMETask: Task[Option[Boolean]] =
          httpIO.startMEAMEServer(samplerate, segmentLength, selectChannels)

        val wsAgentServerPipe = wsIO.webSocketServerAgentObserver

        val a = commandQueue.dequeue flatMap { command =>
          val theShow = Stream.eval(configAndStartMEAMETask) flatMap { ret =>

            ret match {
              case None => Stream.empty
              case Some(b) => {
                if(!b){
                  println("MEAME returned negative")
                  Stream.empty
                }

                else{
                  println("MEAME returned positive")
                  val fug = networkIO.socketStream[Task](networkIO.selectChannelsPort) flatMap {
                    socket =>
                    {
                      val uhh = mainLoop.GArun(
                        socket.reads(1024*1024).through(utilz.bytesToInts),

                        (s: Stream[Task,Byte]) => s.drain,

                        // (s: Stream[Task,Agent]) => s.drain,
                        wsAgentServerPipe,

                        selectChannels
                      )
                      Stream.eval(uhh)
                    }
                  }
                  fug
                }
              }
            }
          }
          theShow
        }
        Stream.eval(httpServerTask).mergeDrainL(a)
      }
    }

    doThing.run
  }


  /**
    * Starts the MEAME server and runs both select and full channel data through provided sinks
    */
  // def ghettoRunFromTCP(
  //   segmentLength: Int,
  //   selectChannels: List[Int]
  // ): Task[Unit] = {

  //   val configAndStartMEAMETask =
  //     httpIO.startMEAMEServer(samplerate, segmentLength, selectChannels)

  //   val theShow = Stream.eval(configAndStartMEAMETask) flatMap { ret =>
  //     ret match {
  //       case None => Stream.empty
  //       case Some(b) => {
  //         if(!b)
  //           Stream.empty
  //         else{
  //           val agentRun = Stream.eval(networkIO.ghetto[Task])
  //           val memeSink: Sink[Task,Byte] = (s: Stream[Task,Byte]) => s
  //             .through(utilz.bytesToInts)
  //             .through(wsIO.webSocketServerConsumer)

  //           val wsRun = Stream.eval(networkIO.streamAllChannels(memeSink))
  //           wsRun merge agentRun
  //         }
  //       }
  //     }
  //   }
  //   theShow.run
  // }


  def testHttp(
    segmentLength: Int,
    selectChannels: List[Int]
  ): Task[Unit] = {

    val configAndStartMEAMETask =
      httpIO.startMEAMEServer(samplerate, segmentLength ,selectChannels)

    println("testHTTP starting now :)")
    (Stream.eval(configAndStartMEAMETask).through(_.map(println))).run
  }

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
