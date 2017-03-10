package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.io.tcp._

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import java.net.InetSocketAddress

import scala.language.higherKinds
import com.typesafe.config._


object neuroServer {

  import namedACG._

  implicit val tcpACG : AsynchronousChannelGroup = namedACG("tcp")
  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(8)

  def getLineChunk[F[_]](socket: Socket[F]): F[Option[Chunk[Byte]]] = socket.read(1024)
  def getLine[F[_]](l: Option[Chunk[Byte]]) =
    l.map{λ => new String(λ.toArray)}.getOrElse("Nil")

  def writeLine[F[_]](l: String, socket: Socket[F]): F[Unit] =
    socket.write(Chunk.indexedSeq(l.getBytes()))

  val conf = ConfigFactory.load()
  val netConf = conf.getConfig("io")
  val addressConf = netConf.getConfig(netConf.getString("target"))

  val ip = addressConf.getString("ip")
  val port = addressConf.getInt("port")
  val socketAddress = new InetSocketAddress(ip, port)

  val reuseAddress = true
  val sendBufferSize = netConf.getInt("sendBufSize")
  val receiveBufferSize = netConf.getInt("recvBufSize")
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

    val memer = Assemblers.assembleExperiment( reads, writes )

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
