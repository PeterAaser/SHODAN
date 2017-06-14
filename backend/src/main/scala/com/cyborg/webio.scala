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

  import params.webSocket._

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


  /**
    Creates a ws server for an agent and attaches it as an observer
    */
  def webSocketServerAgentObserver: Pipe[Task,Agent,Agent] = s => {
    val sink: Sink[Task,Agent] = s => {
      Stream.eval(wsSendOnlyServer(s, agentPort))
    }
    pipe.observeAsync(s, 100000)(sink)
  }


  /**
    Creates a ws server for waveform data and attaches it as a consumer
    Does a lot of assumptions, beware
    */
  def webSocketWaveformObserver: Pipe[Task,Int,Int] = s => {
    val sink: Sink[Task,Int] = s => {
      val downsampled = s
        .through(graphDownSampler(params.waveformVisualizer.blockSize))
        .through(utilz.vectorize(params.waveformVisualizer.wfMsgSize))

      Stream.eval(wsSendOnlyServer[Task,Vector[Int]](downsampled, dataPort))
    }
    pipe.observeAsync(s, 100000)(sink)
  }


  /**
    Downsamples a dataStream such that it can fill a waveForm of vizLength pixels
    blockSize is the amount of datapoints needed to fill a single pixel.
    Be careful to not run this on a muxed stream unless blockSize is a multiple of segment length

    not very efficient, can be tuned if necessary
    */

  def graphDownSampler[F[_]](blockSize: Int): Pipe[F,Int,Int] = {
    println(s"downsampler uses blocksize $blockSize")
    def go: Handle[F,Int] => Pull[F,Int,Unit] = h => {
      h.receive {
        case (chunk, h) => {
          val samples = (0 until chunk.size).indices.collect{
            case i if i % blockSize == 0 => chunk(i)
          }
          h.push(Chunk.seq(chunk.toVector.takeRight(chunk.size % blockSize)))

          Pull.output(Chunk.seq(samples)) >> go(h)
        }
      }
    }
    _.pull(go)
  }
}
