package com.cyborg

import fs2.util.Async

import fs2._
import java.net.InetSocketAddress

import spinoco.fs2.http

import fs2._
import spinoco.fs2.http
import spinoco.fs2.http.routing._
import spinoco.fs2.http.websocket._
import spinoco.fs2.http.HttpResponse

import scodec.codecs.implicits._

import com.cyborg.wallAvoid._

object wsIO {

  import backendImplicits._
  import sharedImplicits._

  // hardcoded
  val serverPort = 9090
  val textPort = 9091
  val agentPort = 9092

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


  def agentWsPipe[F[_]:Async](inStream: Stream[F,Agent]):
      Pipe[F, Frame[Int], Frame[Agent]] = { inbound =>

    val output = inStream.map(Frame.Binary.apply)
    inbound.mergeDrainL(output)
  }


  def agentRouter[F[_]: Async](inStream: Stream[F,Agent]): Route[F] = {
    val ws = websocket[F,Int,Agent]()

    ws map {
      case socket: (Pipe[F,Frame[Int],Frame[Agent]] => Stream[F,HttpResponse[F]]) =>
        {
          println("Agent socket thing is happening")
          socket(agentWsPipe(inStream))
        }
    }
  }


  def server(inStream: Stream[Task,Vector[Int]]): Task[Unit] = {
    val server = http.server(new InetSocketAddress("127.0.0.1", 9090))(route(router(inStream)))
    server.run
  }


  def textServer(inStream: Stream[Task,Vector[Int]]): Task[Unit] = {
    val server = http.server[Task](new InetSocketAddress("127.0.0.1", 9091))(route(textRouter(inStream)))
    server.run
  }


  def agentServer(inStream: Stream[Task,Agent]): Task[Unit] = {
    val server = http.server[Task](new InetSocketAddress("127.0.0.1", 9092))(route(agentRouter(inStream)))
    server.run
  }


  /**
    Creates a ws server and attaches it as a sink via observe
    */
  def attachWebSocketServerSink: Pipe[Task,Int,Int] = s => {

    val sink: Sink[Task,Int] = s => {
      val a = s
        .through(graphDownSampler(blockSize))
        .through(utilz.vectorize(1000))
        .through(_.map(λ => {println(s"ws server sink getting thing"); λ}))

      val b = server(a)
      Stream.eval(b)
    }

    pipe.observeAsync(s, 1024*1024)(sink)
  }


  /**
    Creates a ws server and attaches it as a sink
    */
  def webSocketServerConsumer: Sink[Task,Int] = s => {

    val sink: Sink[Task,Int] = s => {
      val a = s
        .through(graphDownSampler(blockSize))
        .through(utilz.vectorize(1000))

      val b = server(a)
      Stream.eval(b)
    }

    s.through(sink)
  }


  /**
    Creates a ws server for an agent and attaches it as an observer
    */
  def webSocketServerAgentObserver: Pipe[Task,Agent,Agent] = s => {
    val sink: Sink[Task,Agent] = s => {
      Stream.eval(agentServer(s))
    }
    // TODO should probably be async
    pipe.observe(s)(sink)
  }


  // hardcoded
  val vizHeight = 60
  val vizLength = 200
  val pointsPerSec = 40000
  val scalingFactor = 2000

  val blockSize = pointsPerSec/vizLength
  /**
    Downsamples a dataStream such that it can fill a waveForm of vizLength pixels
    blockSize is the amount of datapoints needed to fill a single pixel.
    Be careful to not run this on a muxed stream unless blockSize is a multiple of segment length

    can be tuned if necessary
    */
  // Currently lets through 200 per 40k
  def graphDownSampler[F[_]](blockSize: Int): Pipe[F,Int,Int] = {
    def go: Handle[F,Int] => Pull[F,Int,Unit] = h => {
      h.awaitN(blockSize) flatMap {
        case (chunks, h) => {
          val waveform = chunks.map(_.toList).flatten
          // "Fixes" annoying deserialize issue
          val smallest = waveform.map(λ => if (λ > 100000) 0 else λ).min
          val largest = waveform.max
          Pull.output1(if (math.abs(smallest) < largest) largest else smallest) >> go(h)
        }
      }
    }
    _.pull(go)
  }
}
