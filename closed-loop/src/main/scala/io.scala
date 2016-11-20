package SHODAN

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.async.mutable.Queue
import fs2.util.syntax._
import fs2.io.file._
import fs2.io.tcp._

import java.nio.file._
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import scala.concurrent.duration._
import java.lang.Thread.UncaughtExceptionHandler
import java.net.InetSocketAddress
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import scala.language.higherKinds

object neurIO {

  import utilz._
  import namedACG._

  val ip = "129.241.111.251"
  val port = 1248
  val address = new InetSocketAddress(ip, port)

  val reuseAddress = true
  val sendBufferSize = 256*1024
  val receiveBufferSize = 256*1024
  val keepAlive = true
  val noDelay = true

  def createClientStream[F[_]: Async]
  ( ip: String
  , port: Int
  , reuseAddress: Boolean
  , sendBufferSize: Int
  , receiveBufferSize: Int
  , keepAlive: Boolean
  , noDelay: Boolean)
      : (Stream[F, Socket[F]]) = {

    val address = new InetSocketAddress(ip, port)

    implicit val tcpACG : AsynchronousChannelGroup = namedACG("tcp")

    val clientStream: Stream[F,Socket[F]] = client(
        address
      , reuseAddress
      , sendBufferSize
      , receiveBufferSize
      , keepAlive
      , noDelay
    )

    clientStream
  }
}
