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
}

@RPC
trait NotificationsServerRPC {
  def register(): Future[Unit]
  def unregister(): Future[Unit]
}

@RPC
trait VisualizerRPC {
  def register(): Future[Unit]
  def unregister(): Future[Unit]
}
