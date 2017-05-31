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

object networkIO {

  implicit val tcpACG : AsynchronousChannelGroup = namedACG.namedACG("tcp")
  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(16, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(16)

  val conf = ConfigFactory.load()
  val netConf = conf.getConfig("io")
  val addressConf = netConf.getConfig(netConf.getString("target"))

  val ip = addressConf.getString("ip")
  val port = addressConf.getInt("port")
  val socketAddress = new InetSocketAddress(ip, port)

  val allChannelsPort = 12340
  val selectChannelsPort = 12341

  val reuseAddress = true
  val sendBufferSize = netConf.getInt("sendBufSize")
  val receiveBufferSize = netConf.getInt("recvBufSize")
  val keepAlive = true
  val noDelay = true

  val maxQueued = 3

  def socketStream[F[_]: Async](port: Int): Stream[F, Socket[F]] =
    client(
      new InetSocketAddress(ip, port),
      reuseAddress,
      sendBufferSize,
      receiveBufferSize,
      keepAlive,
      noDelay)


  def streamAllChannels[F[_]:Async](sink: Sink[F,Byte]): F[Unit] = {
    val throughSink = socketStream[F](allChannelsPort) flatMap { socket =>
      socket.reads(1024*1024).through(sink)
    }
    throughSink.run
  }


  def ghetto[F[_]:Async]: F[Unit] = {
    val a = socketStream(selectChannelsPort) flatMap { socket =>
      val a = mainLoop.GArun(
        socket.reads(1024*1024).through(utilz.bytesToInts),
        socket.writes(None),
        List[Int](1,2,3))
      Stream.eval(a)
    }
    a.run
  }


  def streamSelectChannels[F[_]:Async](sink: Sink[F,Byte], stream: Stream[F,Byte]): F[Unit] = {
    val throughSink = socketStream[F](selectChannelsPort) flatMap { socket =>
      socket.reads(1024*1024).through(sink) merge
      stream.through(socket.writes())
    }
    throughSink.run
  }


  def rawDataStream(socket: Socket[Task]): Stream[Task,Int] =
    socket.reads(1024*1024)
      .through(utilz.bytesToInts)


  def decodeChannelStreams(dataStream: Stream[Task,Int], segmentLength: Int, nChannels: Int = 60): Stream[Task,Vector[Stream[Task,Int]]] =
    utilz.alternator(dataStream, segmentLength, nChannels, 1000)

}

object namedACG {

  /**
    Lifted verbatim from fs2 tests.
    I have no idea what it does, but it makes stuff work...
    */

  import java.nio.channels.AsynchronousChannelGroup
  import java.lang.Thread.UncaughtExceptionHandler
  import java.nio.channels.spi.AsynchronousChannelProvider
  import java.util.concurrent.ThreadFactory
  import java.util.concurrent.atomic.AtomicInteger

  def namedACG(name:String):AsynchronousChannelGroup = {
    val idx = new AtomicInteger(0)
    AsynchronousChannelProvider.provider().openAsynchronousChannelGroup(
      16
        , new ThreadFactory {
        def newThread(r: Runnable): Thread = {
          val t = new Thread(r, s"fs2-AG-$name-${idx.incrementAndGet() }")
          t.setDaemon(true)
          t.setUncaughtExceptionHandler(
            new UncaughtExceptionHandler {
              def uncaughtException(t: Thread, e: Throwable): Unit = {
                println("----------- UNHANDLED EXCEPTION ---------")
                e.printStackTrace()
              }
            })
          t
        }
      }
    )
  }
}
