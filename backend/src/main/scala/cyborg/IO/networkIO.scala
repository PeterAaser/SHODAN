package cyborg.io.network
import cats._
import cats.implicits._
import cats.effect._
import cyborg._

import fs2._
import fs2.Stream._
import fs2.concurrent.Topic
import fs2.io.tcp._

import java.net.InetSocketAddress


import scala.language.higherKinds

import utilz._
import backendImplicits._
import params.TCP._

object networkIO {


  val reuseAddress = true
  val keepAlive = true
  val noDelay = true

  val ip = params.TCP.ip
  val port = params.TCP.port
  val hosePort = 12350

  val maxQueued = 3

  def socketStream[F[_]: ConcurrentEffect : ContextShift](port: Int): Resource[F, Socket[F]] =
    client(
      new InetSocketAddress(ip, port),
      reuseAddress,
      sendBufSize,
      recvBufSize,
      keepAlive,
      noDelay)



  def streamAllChannels[F[_]: ConcurrentEffect : ContextShift]: Stream[F, Int] = {
    Stream.resource(socketStream[F](port)) flatMap { socket =>
      socket.reads(1024*1024).through(utilz.bytesToInts(params.TCP.format))
    }
  }


  /**
    Untested crashy ghetto shit
    */
  def channelServer[F[_]: ConcurrentEffect : ContextShift](topics: List[Topic[F,TaggedSegment]]): Stream[F,F[Unit]] = {
    def hoseData(socket: Socket[F]): F[Unit] = {
      socket.reads(16, None)
        .through(_.map(_.toInt)).flatMap { channel =>
          topics(channel).subscribe(100)
            .map{ d =>
              val bb = java.nio.ByteBuffer.allocate(d.data.size*4)
              for(ii <- 0 until d.data.size){
                bb.putInt(d.data(ii))
              }
              Chunk.seq(bb.array())
            }
            .through(chunkify)
            .through(socket.writes(None))
        }.compile.drain
    }

    server(new InetSocketAddress("0.0.0.0", hosePort)).flatMap {
      say("opening server")
      sockets => {
        Stream.resource(sockets).map{ socket =>
          say(s"got a socket")
          hoseData(socket)
        }
      }
    }
  }
}
