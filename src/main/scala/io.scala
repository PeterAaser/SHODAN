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

object neurIO {

  import utilz._
  import namedACG._

  implicit val strategy: Strategy = Strategy.fromFixedDaemonPool(8)
  implicit val tcpACG : AsynchronousChannelGroup = namedACG("tcp")
  implicit val scheduler: Scheduler = Scheduler.fromFixedDaemonPool(8)

  val ip = "129.241.111.251"
  val port = 1248
  val address = new InetSocketAddress(ip, port)

  val reuseAddress = true
  val sendBufferSize = 256*1024
  val receiveBufferSize = 256*1024
  val keepAlive = true
  val noDelay = true


  // TODO lag ordentlige metoder
  val (byteStream: Stream[Task, Byte], byteSink: Sink[Task,Byte]) =
    createClientStream(
        ip
      , port
      , reuseAddress
      , sendBufferSize
      , receiveBufferSize
      , keepAlive
      , noDelay
    )

  val intStream: Stream[Task, Int] =
    byteStream.through(utilz.bytesToInts)

  val multiStream = utilz.alternate(intStream, 64, 256*256, 4)

  val crash = multiStream.flatMap { xs => (
  xs(0).map(λ => print (s"[1 - $λ] ")) zip
  xs(1).map(λ => print (s"[2 - $λ] "))
  ) zip (
  xs(2).map(λ => print (s"[3 - $λ] ")) zip
  xs(3).map(λ => print (s"[4 - $λ] ")))
  }

  // def explode = intStream.run.unsafeRunAsyncFuture
  def explode = crash.run.unsafeRunAsyncFuture

  def createClientStream
  ( ip: String
  , port: Int
  , reuseAddress: Boolean
  , sendBufferSize: Int
  , receiveBufferSize: Int
  , keepAlive: Boolean
  , noDelay: Boolean)
      : (Stream[Task, Byte], Sink[Task, Byte]) = {

    val address = new InetSocketAddress(ip, port)

    val clientStream: Stream[Task,Socket[Task]] = client(
        address
      , reuseAddress
      , sendBufferSize
      , receiveBufferSize
      , keepAlive
      , noDelay
    )

    // very sorry. FlatMap must return a stream.
    // There is surely a better way than whatever the fuck this thing is
    var s: Sink[Task,Byte] = {
      def go: Handle[Task,Byte] => Pull[Task,Unit,Unit] = h => {
        h.receive1 { (d, h) => go(h)}}
      _.pull(go)
    }

    (clientStream.flatMap { x => s = x.writes(None); x.reads(256, None)},
     s)
  }
}
