//   import cats.effect.IO
//   import cats.effect.Effect
//   import cats.effect.Async

//   import fs2._
//   import java.net.InetSocketAddress
//   import scala.concurrent.ExecutionContext

//   import scodec.codecs.implicits._
//   import scodec.Codec

// object wsIO {


//   import cyborg.wallAvoid._

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
