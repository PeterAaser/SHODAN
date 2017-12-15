package cyborg

import cats.effect.Effect
import fs2._
import fs2.Stream._
import fs2.io.tcp._

import java.net.InetSocketAddress

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext

import scala.language.higherKinds


object networkIO {

  import backendImplicits._
  import params.TCP._

  val reuseAddress = true
  val keepAlive = true
  val noDelay = true

  val ip = params.TCP.ip
  // val port = params.TCP.port
  val port = params.TCP.sawtooth

  val maxQueued = 3

  def socketStream[F[_]: Effect](port: Int)(implicit ec: ExecutionContext): Stream[F, Socket[F]] =
    client(
      new InetSocketAddress(ip, port),
      reuseAddress,
      sendBufSize,
      recvBufSize,
      keepAlive,
      noDelay)


  def streamAllChannels[F[_]: Effect](implicit ec: ExecutionContext): Stream[F, Int] = {
    println(s"streaming from IP: $ip, port: $port")
    socketStream[F](port) flatMap { socket =>
      socket.reads(1024*1024).through(utilz.bytesToInts)
    }
  }

}
