package com.cyborg

import fs2.async.mutable.Queue
import fs2.util.Async
import java.net.InetSocketAddress

import fs2._

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

  def router[F[_]: Async](inStream: Stream[F,Vector[Int]]): Route[F] = {
    val ws: Match[Nothing, (Pipe[F, Frame[Int], Frame[Vector[Int]]]) => Stream[F, HttpResponse[F]]]
      = websocket[F,Int,Vector[Int]]()

    ws map {
      case socket: (Pipe[F,Frame[Int],Frame[Vector[Int]]] => Stream[F,HttpResponse[F]]) =>
        socket(wsPipe(inStream))
    }
  }


  // A pipe that ignores input and outputs stuff from inStream
  def wsPipe[F[_]:Async](inStream: Stream[F,Vector[Int]]):
      Pipe[F, Frame[Int], Frame[Vector[Int]]] = { inbound =>

    val output = inStream
      .through(_.map ( λ => { println(s" converting input to frame :--DDd "); λ }))
      .through(_.map(Frame.Binary(_)))
    inbound.mergeDrainL(output)
  }


  def server(inStream: Stream[Task,Vector[Int]]) = http.client[Task]().flatMap { client =>
    val request = WebSocketRequest.ws("127.0.0.1", 9090, "/channelData")
    client.
      websocket(request, wsPipe(inStream)).run
  }
}
