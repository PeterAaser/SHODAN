package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.io.tcp._

import java.net.InetSocketAddress

import java.net.InetSocketAddress

import scala.language.higherKinds
import com.typesafe.config._


object networkIO {

  import backendImplicits._

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


  def streamSelectChannels[F[_]:Async](sink: Sink[F,Byte]): F[Unit] = {
    val throughSink = socketStream[F](selectChannelsPort) flatMap { socket =>
      socket.reads(1024*1024).through(sink)
    }
    throughSink.run
  }
}
