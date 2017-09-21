package com.cyborg

import cats.effect.Effect
import fs2._
import fs2.Stream._
import fs2.io.tcp._

import java.net.InetSocketAddress

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext

import scala.language.higherKinds
import com.typesafe.config._


object networkIO {

  import backendImplicits._
  import params.TCP._

  val allChannelsPort = 12340
  val selectChannelsPort = 12341

  val reuseAddress = true
  val keepAlive = true
  val noDelay = true

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
    socketStream[F](allChannelsPort) flatMap { socket =>
      socket.reads(1024*1024).through(utilz.bytesToInts)
    }
  }


  // def sendStimreqStream[F[_]:Async]: Sink[F,Int] = ???
}
