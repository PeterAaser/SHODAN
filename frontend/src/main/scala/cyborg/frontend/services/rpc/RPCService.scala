package cyborg.frontend.services.rpc

import cyborg.shared.rpc.client._
import cyborg.wallAvoid.Agent
import scala.collection.mutable
import cyborg.frontend.Context

import cyborg._
import cyborg.State._
import frontilz._
import cyborg.Settings._
import cyborg.RPCmessages._

class RPCService extends MainClientRPC {
  override def wf(): WfClientRPC = WfClient
  override def agent(): AgentClientRPC = AgentClient
  override def state(): ClientStateRPC = StateClient
}

object StateClient extends ClientStateRPC {
  def pushConfig(c: FullSettings): Unit = ???
  def pushState(s: ProgramState): Unit  = ???
}

object WfClient extends WfClientRPC {

  var onWfUpdate: Array[Int] => Unit = (_: Array[Int]) => ()
  var wfRunning = false
  // var num = 0

  override def wfPush(data: Array[Int]): Unit = onWfUpdate(data)
}

object AgentClient extends AgentClientRPC {

  var onAgentUpdate: Agent => Unit = (_: Agent) => ()
  var agentRunning = false

  override def agentPush(agent: Agent): Unit = onAgentUpdate(agent)
}
