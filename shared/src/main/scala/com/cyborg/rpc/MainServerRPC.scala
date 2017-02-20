package com.cyborg.rpc

import com.avsystem.commons.rpc.RPC
import io.udash.rpc._
import scala.concurrent.Future

@RPC
trait MainServerRPC {
  def hello(name: String): Future[String]
  def pushMe(): Unit
  def ping(id: Int): Future[Int]
  def notifications(): NotificationsServerRPC
  def visualizer(): VisualizerRPC
  def MEAMEControl(): MEAMEControlRPC
}

@RPC
trait NotificationsServerRPC {
  def register(): Future[Unit]
  def unregister(): Future[Unit]
}

@RPC
trait MEAMEControlRPC {
  def start(): Future[Unit]
}

@RPC
trait VisualizerRPC {
  def registerAgent(): Future[Unit]
  def unregisterAgent(): Future[Unit]
}
