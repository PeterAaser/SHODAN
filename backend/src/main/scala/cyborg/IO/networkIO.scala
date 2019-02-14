package cyborg.io.network
import cats._
import cats.data.Kleisli
import cats.implicits._
import cats.effect._
import cyborg.Settings.FullSettings
import cyborg._

import fs2._
import fs2.Stream._
import fs2.concurrent.Topic
import fs2.io.tcp._

import java.net.InetSocketAddress


import scala.language.higherKinds

import utilz._
import backendImplicits._

object networkIO {


  val reuseAddress = true
  val keepAlive = true
  val noDelay = true



  def streamAllChannels[F[_]: ConcurrentEffect : ContextShift]
    = Kleisli[Id, FullSettings, Stream[F,Int]]{ conf =>

      say(s"stream all channels tcp with args: $conf")

      def socketStream: Resource[F, Socket[F]] =
        client(
          new InetSocketAddress(conf.source.meameIP, conf.source.dataPort),
          reuseAddress,
          conf.source.sendBufSize,
          conf.source.recvBufSize,
          keepAlive,
          noDelay)

      Stream.resource(socketStream) flatMap { socket =>
        socket.reads(conf.source.readBufSize).through(utilz.bytesToInts(conf.source.format))
      }
    }
}
