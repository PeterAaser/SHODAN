package cyborg

import cats.effect.IO

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.websocket.WebsocketBits._
import org.http4s.server.websocket._
import scodec.Codec

import utilz._

import fs2._
import fs2.Stream._

import cyborg.wallAvoid._
import sharedImplicits._


object webSocketServer {

  val outSink: Sink[IO,WebSocketFrame] = _.drain

  def toBytes(a: Array[Int]): Array[Byte] = {
    val bb = java.nio.ByteBuffer.allocate(a.length*4)
    for(ii <- 0 until a.length){
      bb.putInt(a(ii))
    }
    bb.array()
  }

  def webSocketWaveformService(waveforms: Stream[IO,Array[Int]]) = {
    val inStream: Stream[IO,WebSocketFrame] = {
      waveforms
        .through(_.map(z => Binary(toBytes(z))))
    }

    def route: HttpService[IO] = HttpService[IO] {
      case GET -> Root => {
        WS[IO](inStream, outSink)
      }
    }
    route
  }


  def webSocketAgentService(agentStream: Stream[IO,Agent]) = {
    val agentInStream: Stream[IO,WebSocketFrame] =
      agentStream.map(z => Binary(Codec.encode(z).require.toByteArray))

    def route: HttpService[IO] = HttpService[IO] {
      case GET -> Root => {
        WS[IO](agentInStream, outSink)
      }
    }
    route
  }


  def webSocketWaveformServer(waveforms: Stream[IO,Array[Int]]) = {
    val service = webSocketWaveformService(waveforms)
    val builder = BlazeBuilder[IO].bindHttp(9091).mountService(service).start
    builder
  }

  def webSocketAgentServer(agentStream: Stream[IO,Agent]) = {
    val service = webSocketAgentService(agentStream)
    val builder = BlazeBuilder[IO].bindHttp(9092).mountService(service).start
    builder
  }
}
