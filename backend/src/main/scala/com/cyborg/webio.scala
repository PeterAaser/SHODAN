package com.cyborg

import fs2.util.Async

import fs2._
import java.net.InetSocketAddress

import spinoco.fs2.http

import fs2._
import spinoco.fs2.http
import spinoco.fs2.http.routing._
import spinoco.fs2.http.websocket._
import spinoco.fs2.http.routing.Matcher.Match
import spinoco.fs2.http.HttpResponse


object wsIO {


  import java.nio.channels.AsynchronousChannelGroup
  import java.util.concurrent.Executors


  val ES = Executors.newCachedThreadPool(Strategy.daemonThreadFactory("AG"))

  implicit val S = Strategy.fromExecutor(ES)
  implicit val Sch = Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(4, Strategy.daemonThreadFactory("S")))
  implicit val AG = AsynchronousChannelGroup.withThreadPool(ES)

  implicit val IntCodec = scodec.codecs.int32
  implicit val IntVectorCodec = scodec.codecs.vectorOfN(scodec.codecs.int32, scodec.codecs.int32)
  implicit val StringCodec = scodec.codecs.utf8_32


  def textPipe[F[_]:Async](inStream: Stream[F,Vector[Int]]): Pipe[F, Frame[Int], Frame[String]] = inbound => {
    val out = inStream.through(_.map(λ => " hello! "))
      .map(λ => { println("sending hello!"); λ})
      .map(Frame.Binary.apply)
    inbound.mergeDrainL(out)
  }

  def textRouter[F[_]: Async](inStream: Stream[F,Vector[Int]]): Route[F] = {
    websocket[F,Int,String]() map {
      case socket: (Pipe[F,Frame[Int],Frame[String]] => Stream[F,HttpResponse[F]]) =>
        socket(textPipe(inStream))
    }
  }

  // A pipe that ignores input and outputs stuff from inStream
  def wsPipe[F[_]:Async](inStream: Stream[F,Vector[Int]]):
      Pipe[F, Frame[Int], Frame[Vector[Int]]] = { inbound =>

    val output = inStream.map(Frame.Binary.apply)

    inbound
      .through(_.map(λ => { println(s"Got inbound message, looks like $λ"); λ}))
      .mergeDrainL(output)
  }

  def router[F[_]: Async](inStream: Stream[F,Vector[Int]]): Route[F] = {
    val ws = websocket[F,Int,Vector[Int]]()

    ws map {
      case socket: (Pipe[F,Frame[Int],Frame[Vector[Int]]] => Stream[F,HttpResponse[F]]) =>
        {
          println("Socket thing is happening")
          socket(wsPipe(inStream))
        }
    }
  }

  def server(inStream: Stream[Task,Vector[Int]]): Task[Unit] = {
    val server = http.server(new InetSocketAddress("127.0.0.1", 9090))(route(router(inStream)))
    server.run
  }


  def textServer(inStream: Stream[Task,Vector[Int]]): Task[Unit] = {
    val server = http.server[Task](new InetSocketAddress("127.0.0.1", 9090))(route(textRouter(inStream)))
    server.run
  }


  /**
    Creates a ws server and attaches it as a sink via observe
    */
  def attachWebSocketServerSink: Pipe[Task,Int,Int] = s => {

    val sink: Sink[Task,Int] = s => {
      val a = s.through(utilz.vectorize(10)).through(_.map(λ => {println(λ); λ}))
      val b = server(a)
      Stream.eval(b)
    }

    pipe.observeAsync(s, 1024*1024)(sink)
  }
}
