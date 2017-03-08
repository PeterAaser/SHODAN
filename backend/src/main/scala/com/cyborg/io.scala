package com.cyborg

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


object neuroServer {

  import utilz._
  import namedACG._

  implicit val tcpACG : AsynchronousChannelGroup = namedACG("tcp")
  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(8)

  def getLineChunk[F[_]](socket: Socket[F]): F[Option[Chunk[Byte]]] = socket.read(1024)
  def getLine[F[_]](l: Option[Chunk[Byte]]) = {
    l.map{λ => new String(λ.toArray)}.getOrElse("Nil")
  }

  def writeLine[F[_]](l: String, socket: Socket[F]): F[Unit] = {
    val bytes = Chunk.indexedSeq(l.getBytes())
    socket.write(bytes)
  }

  // val ip = "129.241.111.251"
  // val port = 1256
  val ip = "129.241.201.110"
  val port = 8899
  val socketAddress = new InetSocketAddress(ip, port)

  val reuseAddress = true
  val sendBufferSize = 1024*4
  val receiveBufferSize = 256*1024
  val keepAlive = true
  val noDelay = true

  val maxQueued = 3

  def c[F[_]: Async]: Stream[F, Socket[F]] =
    client(
      socketAddress,
      reuseAddress,
      sendBufferSize,
      receiveBufferSize,
      keepAlive,
      noDelay)

  def assembleClient[F[_]: Async](socket: Socket[F]): Stream[F, Unit] = {
    val reads: Stream[F, Byte] = socket.reads(1024)
    val writes: Sink[F, Byte] = socket.writes(None)

    val memer = Assemblers.assembleExperiment(
      reads,
      writes,
      40000,
      4
    )
    memer
  }

  def gogo[F[_]: Async]: F[Unit] = {
    val meme = c flatMap { meameSocket =>
      {
        val memer = assembleClient(meameSocket)
        memer
      }
    }
    meme.run
  }
}
