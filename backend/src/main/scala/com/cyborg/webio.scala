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
import scodec.Codec

import com.cyborg.wallAvoid._

object wsIO {

  import backendImplicits._
  import sharedImplicits._

  // hardcoded
  val serverPort = 9090
  val textPort = 9091
  val agentPort = 9092


  def wsSendOnlyPipe[F[_]:Async,I](inStream: Stream[F,I]):
      Pipe[F,Frame[Int],Frame[I]] = inbound =>
  {
    val output = inStream.map(Frame.Binary.apply)
    inbound.mergeDrainL(output)
  }


  def wsSendOnlyRouter[F[_]:Async,I](inStream: Stream[F,I])(implicit c: Codec[I]): Route[F] = {
    val pipe = wsSendOnlyPipe(inStream)
    websocket[F,Int,I]() map {
      case socket: (Pipe[F,Frame[Int],Frame[I]] => Stream[F,HttpResponse[F]]) =>
        socket(pipe)
    }
  }


  def wsSendOnlyServer[F[_]:Async,I](inStream: Stream[F,I], port: Int)(implicit c: Codec[I]) = {
    val router = wsSendOnlyRouter(inStream)
    val server = http.server(new InetSocketAddress("127.0.0.1", port))(route(router))
    server.run
  }


  def agentWsPipe[F[_]:Async](inStream: Stream[F,Agent]):
      Pipe[F, Frame[Int], Frame[Agent]] = { inbound =>

    val output = inStream.map(Frame.Binary.apply)
    inbound.mergeDrainL(output)
  }


  /**
    Creates a ws server for an agent and attaches it as an observer
    */
  def webSocketServerAgentObserver: Pipe[Task,Agent,Agent] = s => {
    val sink: Sink[Task,Agent] = s => {
      Stream.eval(wsSendOnlyServer(s, agentPort))
    }
    pipe.observeAsync(s, 100)(sink)
  }


  // TODO find a more fitting place for this guy

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
