package cyborg

import cats.effect.{ Effect, IO }
import fs2._
import fs2.Stream._
import fs2.async.mutable.Topic
import fs2.io.tcp._

import java.net.InetSocketAddress

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext

import scala.language.higherKinds

import utilz._

object networkIO {

  import backendImplicits._
  import params.TCP._

  val reuseAddress = true
  val keepAlive = true
  val noDelay = true

  val ip = params.TCP.ip
  val port = params.TCP.port
  val hosePort = 12350

  val maxQueued = 3

  def socketStream[F[_]: Effect](port: Int)(implicit ec: EC): Stream[F, Socket[F]] =
    client(
      new InetSocketAddress(ip, port),
      reuseAddress,
      sendBufSize,
      recvBufSize,
      keepAlive,
      noDelay)

  def streamAllChannels[F[_]: Effect](implicit ec: EC): Stream[F, Int] = {
    say(s"streaming from IP: $ip, port: $port")
    socketStream[F](port) flatMap { socket =>
      socket.reads(1024*1024).through(utilz.bytesToInts)
    }
  }


  def channelServer[F[_]: Effect](topics: List[Topic[F,TaggedSegment]])(implicit ec: EC): Stream[F,F[Unit]] = {
    def hoseData(socket: Socket[F]): F[Unit] = {
      socket.reads(16, None)
        .through(_.map(_.toInt)).flatMap { channel =>
          say(s"$channel")
          topics(channel).subscribe(100)
            .through{
              _.map{ d =>
                val bb = java.nio.ByteBuffer.allocate(d.data.length*4)
                for(ii <- 0 until d.data.length){
                  bb.putInt(d.data(ii))
                }
                bb.array().toVector
              }}
            .through(chunkify)
            .through(socket.writes(None))
        }.compile.drain
    }

    server(new InetSocketAddress("129.241.110.224", hosePort)).flatMap {
      say("opening server")
      sockets => {
        sockets.map{ socket =>
          say(s"got a socket")
          hoseData(socket)
        }
      }
    }
  }
}
