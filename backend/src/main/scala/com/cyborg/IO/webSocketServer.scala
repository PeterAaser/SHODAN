package com.cyborg

object webSocketServer {

  import io.circe.literal._
  import io.circe.generic.auto._
  import io.circe.syntax._

  import cats.effect.IO
  import scala.concurrent.ExecutionContext

  import org.http4s._
  import org.http4s.dsl._
  import org.http4s.server.Server
  import org.http4s.headers.`Cache-Control`
  import org.http4s.CacheDirective.`no-cache`
  import org.http4s.client.blaze._
  import org.http4s.Uri
  import org.http4s.server.blaze.BlazeBuilder
  import org.http4s.websocket.WebsocketBits._
  import org.http4s.server.websocket._
  import scodec.Codec

  import utilz._

  import fs2._
  import fs2.Stream._

  import com.cyborg.wallAvoid._
  import sharedImplicits._


  def webSocketService(waveforms: List[DataTopic[IO]], agent: Stream[IO,Agent]) = {

    // TODO: Currently ignores segments etc
    val inStream: Stream[IO,WebSocketFrame] = {
      val dur = waveforms
        .map(_.subscribe(100))

      Stream.emit(dur).covary[IO]
        .through(roundRobin)
        .through(chunkify)
        .through(_.map(_._1))
        .through(chunkify)
        .through(downSamplePipe(1000))
        .through(intToBytes)
        .through(_.map(Binary(_)))
    }

    def agentInStream(s: Stream[IO,Agent]): Stream[IO,WebSocketFrame] =
      s.map(λ => Binary(Codec.encode(λ).require.toByteArray))

    val outSink: Sink[IO,WebSocketFrame] = _.drain


    def route: HttpService[IO] = HttpService[IO] {
      case GET -> Root / "ws" / "wave" =>
        WS[IO](inStream, outSink)

      case GET -> Root / "ws" / "agent" =>
        WS[IO](agentInStream(agent), outSink)
    }
    route
  }

  def webSocketServer(waveforms: List[DataTopic[IO]], agent: Stream[IO,Agent]) = {
    val service = webSocketService(waveforms, agent)
    val builder = BlazeBuilder[IO].bindHttp(8080).mountService(service).start
    builder
  }
}

// object wsIO {

//   import cats.effect.IO
//   import cats.effect.Effect
//   import cats.effect.Async

//   import fs2._
//   import java.net.InetSocketAddress
//   import scala.concurrent.ExecutionContext

//   import scodec.codecs.implicits._
//   import scodec.Codec

//   import com.cyborg.wallAvoid._

//   def wsSendOnlyPipe[F[_]: Effect, I](inStream: Stream[F,I])(implicit ec: ExecutionContext): Pipe[F,Frame[Int],Frame[I]] = {

//     val output = inStream.map(Frame.Binary.apply)
//     inbound => inbound.drain.concurrently(output)
//   }

//   def wsSendOnlyRouter[F[_]: Effect, I](inStream: Stream[F,I])(implicit c: Codec[I], ec: ExecutionContext): Route[F] = {

//     val pipe = wsSendOnlyPipe(inStream)
//     websocket[F,Int,I]() map {
//       case socket: (Pipe[F,Frame[Int],Frame[I]] => Stream[F,HttpResponse[F]]) =>
//         socket(pipe)
//     }
//   }

//   def wsSendOnlyServer[F[_]: Effect ,I](inStream: Stream[F,I], port: Int)(implicit c: Codec[I], ec: ExecutionContext) = {
//     val router = wsSendOnlyRouter(inStream)
//     val server = http.server(new InetSocketAddress("127.0.0.1", port))(route(router))
//     server.run
//   }

//   /**
//     Creates a ws server for an agent and attaches it as an observer
//     */
//   def webSocketServerAgentObserver(implicit ec: ExecutionContext): Sink[IO,Agent] = s => {
//     val sink: Sink[IO,Agent] = s => {
//       Stream.eval(wsSendOnlyServer(s, agentPort))
//     }
//     s.through(sink)
//   }

//   /**
//     Creates a ws server for waveform data and attaches it as a consumer
//     */
//   def webSocketWaveformSink[F[_]: Effect](s: Stream[F,Vector[Int]])(implicit ec: ExecutionContext): Stream[F,Unit] =
//     Stream.eval(wsSendOnlyServer[F,Vector[Int]](s, dataPort))

//   /**
//     Downsamples a dataStream such that it can fill a waveForm of vizLength pixels
//     blockSize is the amount of datapoints needed to fill a single pixel.
//     Be careful to not run this on a muxed stream unless blockSize is a multiple of segment length

//     not very efficient, can be tuned if necessary
//     */

//   // TODO: Either kill, use the util downsampler, or unfuckulate
//   def graphDownSampler[F[_]](blockSize: Int): Pipe[F,Int,Int] = {
//     println("WARNING: THIS FUNCTION IS NOT CURRENTLY WELL BEHAVED")
//     println("WARNING: THIS FUNCTION IS NOT CURRENTLY WELL BEHAVED")
//     println("WARNING: THIS FUNCTION IS NOT CURRENTLY WELL BEHAVED")
//     println("WARNING: THIS FUNCTION IS NOT CURRENTLY WELL BEHAVED")
//     println(s"downsampler uses blocksize $blockSize")
//     def go(s: Stream[F,Int]): Pull[F,Int,Unit] = {
//       s.pull.unconsChunk flatMap {
//         case Some((chunk, tl)) => {
//           val samples = (0 until chunk.size).indices.collect{
//             case i if i % blockSize == 0 => chunk(i)
//           }
//           // TODO: this doesn't translate to .10
//           // tl.push(Chunk.seq(chunk.toVector.takeRight(chunk.size % blockSize)))

//           Pull.output(Chunk.seq(samples)) >> go(tl)
//         }
//       }
//     }
//     in => go(in).stream
//   }
// }
