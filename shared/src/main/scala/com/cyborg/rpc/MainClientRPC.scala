package com.cyborg.rpc

import com.avsystem.commons.rpc.RPC
import com.cyborg.wallAvoid.Agent
import io.udash.rpc._

@RPC
trait MainClientRPC {
  def push(number: Int): Unit
  def notifications(): NotificationsClientRPC
}

@RPC
trait NotificationsClientRPC {
  def notify(msg: String): Unit
}

@RPC
trait ClientVisualizerRPC {
  def update(agent: Agent): Unit
}
