package cyborg.frontend.services.rpc

import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.client.WfClientRPC
import cyborg.shared.rpc.client.AgentClientRPC
import cyborg.wallAvoid.Agent
import scala.collection.mutable
import cyborg.frontend.Context

import cyborg._
import frontilz._

class RPCService extends MainClientRPC {
  override def wf(): WfClientRPC = WfClient
  override def agent(): AgentClientRPC = AgentClient
}

object WfClient extends WfClientRPC {

  var onWfUpdate: Array[Int] => Unit = (_: Array[Int]) => ()
  var wfRunning = false
  // var num = 0


  override def wfPush(data: Array[Int]): Unit = onWfUpdate(data)

  // Kinda magical on the registering front
  def register(q: mutable.Queue[Array[Int]]): Unit = {
    onWfUpdate = (data: Array[Int]) => q.enqueue(data)

    wfRunning = true
    Context.serverRpc.registerWaveform
  }

  def unregister(): Unit = {
    wfRunning = false

    Context.serverRpc.unregisterWaveform
  }
}

object AgentClient extends AgentClientRPC {

  var onAgentUpdate: Agent => Unit = (_: Agent) => ()
  var agentRunning = false

  override def agentPush(agent: Agent): Unit = onAgentUpdate(agent)

  def register(agentQueue: mutable.Queue[Agent]): Unit = {
    onAgentUpdate = (agent: Agent) => agentQueue.enqueue(agent)

    agentRunning = true
    Context.serverRpc.registerAgent
  }

  def unregister(): Unit = {
    agentRunning = false

    Context.serverRpc.unregisterAgent
  }
}
