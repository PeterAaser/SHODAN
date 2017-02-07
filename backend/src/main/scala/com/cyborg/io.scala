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

  def createInetSockAddress(ip: String, port: String): InetSocketAddress = {
    new InetSocketAddress("0", 0)
  }


  def getLineChunk[F[_]](socket: Socket[F]): F[Option[Chunk[Byte]]] = socket.read(1024)
  def getLine[F[_]](l: Option[Chunk[Byte]]) = {
    l.map{λ => new String(λ.toArray)}.getOrElse("Nil")
  }

  def writeLine[F[_]](l: String, socket: Socket[F]): F[Unit] = {
    val bytes = Chunk.indexedSeq(l.getBytes())
    socket.write(bytes)
  }


  val ip = "129.241.111.251"
  val port = 9898
  val socketAddress = new InetSocketAddress(ip, port)

  val reuseAddress = true
  val sendBufferSize = 256*1024
  val receiveBufferSize = 256*1024
  val keepAlive = true
  val noDelay = true

  val maxQueued = 3

  def s[F[_]: Async]: Stream[F, Stream[F, Socket[F]]] =
    server[F](socketAddress, maxQueued, reuseAddress, 1024)

  def handleConnection[F[_]: Async](socket: Stream[F, Socket[F]]): Stream[F, Unit] = {
    socket.flatMap {
      socket: Socket[F] => {

        println("new connection")
        val readStream: Stream[F, Byte] = socket.reads(1024)
        val writeStream: Sink[F, Byte] = socket.writes(None)

        val meme = for {
          reservoirIP <- getLineChunk[F](socket)
          reservoirPort <- getLineChunk[F](socket)

          visualizerIP <- getLineChunk[F](socket)
          visualizerPort <- getLineChunk[F](socket)
        } yield {
          println(reservoirIP)
          println(reservoirPort)
          println(visualizerIP)
          println(visualizerPort)

          val cheat1 = new InetSocketAddress("129.241.111.251", 9898)
          val cheat2 = new InetSocketAddress("129.241.111.251", 9898)

          assembleIO(cheat1, cheat2)

        }

        val meme2 = Stream.eval(meme).flatMap { λ => Stream.eval(λ) }
        meme2
      }
    }
  }

  def runServer[F[_]: Async]: F[Unit] =
    s.flatMap {  socket: Stream[F, Socket[F]] =>
      {
        handleConnection(socket)
      }
    }.run

  def assembleIO[F[_]: Async](
    reservoir: InetSocketAddress,
    visualizer: InetSocketAddress): F[Unit] =
  {

    val meameClient: Stream[F,Socket[F]] = client(
      reservoir
        , reuseAddress
        , sendBufferSize
        , receiveBufferSize
        , keepAlive
        , noDelay
    )

    val visualizerClient: Stream[F,Socket[F]] = client(
      visualizer
        , reuseAddress
        , sendBufferSize
        , receiveBufferSize
        , keepAlive
        , noDelay
    )

    val meme = meameClient.flatMap {
      meameSocket: Socket[F] => {
        visualizerClient.flatMap {
          visualizerSocket: Socket[F] => {

            val meameReadStream: Stream[F, Byte] = meameSocket.reads(1024)
            val meameWriteSink: Sink[F, Byte] = meameSocket.writes(None)

            val visualizerWriteStream: Sink[F, Byte] = visualizerSocket.writes(None)

            val memer = Assemblers.assembleExperiment(
              meameReadStream,
              meameWriteSink,
              visualizerWriteStream,
              40000,
              4
            )

            memer
          }
        }
      }
    }

    meme.run
  }
}
