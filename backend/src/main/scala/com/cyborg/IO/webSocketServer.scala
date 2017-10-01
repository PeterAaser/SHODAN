package com.cyborg

object webSocketServer {

  import cats.effect.IO

  import org.http4s._
  import org.http4s.dsl._
  import org.http4s.server.blaze.BlazeBuilder
  import org.http4s.websocket.WebsocketBits._
  import org.http4s.server.websocket._
  import scodec.Codec

  import utilz._

  import fs2._
  import fs2.Stream._

  import com.cyborg.wallAvoid._
  import sharedImplicits._

  val outSink: Sink[IO,WebSocketFrame] = _.drain


  def webSocketWaveformService(waveforms: Stream[IO,Int]) = {
    val inStream: Stream[IO,WebSocketFrame] = {
      waveforms
        .through(intToBytes)
        .through(_.map(Binary(_)))
    }

    def route: HttpService[IO] = HttpService[IO] {
      case GET -> Root / "ws" / "wave" =>
        WS[IO](inStream, outSink)
    }
    route
  }


  def webSocketAgentService(agentStream: Stream[IO,Agent]) = {
    val agentInStream: Stream[IO,WebSocketFrame] =
      agentStream.map(λ => Binary(Codec.encode(λ).require.toByteArray))

    def route: HttpService[IO] = HttpService[IO] {
      case GET -> Root / "ws" / "agent" =>
        WS[IO](agentInStream, outSink)
    }
    route
  }


  def webSocketWaveformServer(waveforms: Stream[IO,Int]) = {
    val service = webSocketWaveformService(waveforms)
    val builder = BlazeBuilder[IO].bindHttp(8080).mountService(service).start
    builder
  }

  def webSocketAgentServer(agentStream: Stream[IO,Agent]) = {
    val service = webSocketAgentService(agentStream)
    val builder = BlazeBuilder[IO].bindHttp(8081).mountService(service).start
    builder
  }
}
