package com.cyborg.rpc

import com.avsystem.commons.rpc.RPC
import com.cyborg.wallAvoid.Agent
import io.udash.rpc._

@RPC
trait MainClientRPC {
  def push(number: Int): Unit
  def notifications(): NotificationsClientRPC
  def visualizer(): ClientVisualizerRPC
}

@RPC
trait NotificationsClientRPC {
  def notify(msg: String): Unit
}

@RPC
trait ClientVisualizerRPC {
  def update(agent: Agent): Unit
}
