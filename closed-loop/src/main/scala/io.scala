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

object neuroServer {

  import utilz._
  import namedACG._

  implicit val tcpACG : AsynchronousChannelGroup = namedACG("tcp")
  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(8)


  def getLineChunk[F[_]](socket: Socket[Task]): F[Option[Chunk[Byte]]] = socket.read(1024)
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

  val s: Stream[Task, Stream[Task, Socket[Task]]] =
    server[Task](socketAddress, maxQueued, reuseAddress, 1024)

  def handleConnection(socket: Stream[Task, Socket[Task]]): Stream[Task, Unit] = {
    socket.flatMap {
      socket: Socket[Task] => {

        println("new connection")
        val readStream: Stream[Task, Byte] = socket.reads(1024)
        val writeStream: Sink[Task, Byte] = socket.writes(None)

        val meme = for {
          reservoirIP <- getLineChunk[Task](socket)
          reservoirPort <- getLineChunk[Task](socket)

          visualizerIP <- getLineChunk[Task](socket)
          visualizerPort <- getLineChunk[Task](socket)
        } yield {
          println(reservoirIP)
          println(reservoirPort)
          println(visualizerIP)
          println(visualizerPort)
          // some task of unit
        }

        ???
      }
    }
  }

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
