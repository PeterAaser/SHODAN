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

  var onStatePush: ProgramState => Unit = (_: ProgramState) => ()
  var onConfPush: FullSettings => Unit = (_: FullSettings) => ()

  def pushConfig(c: FullSettings): Unit = onConfPush(c)
  def pushState(s: ProgramState): Unit  = onStatePush(s)
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

object Hurr {
  def register(agent: scala.collection.mutable.Queue[Agent],
               wf: scala.collection.mutable.Queue[Array[Int]],
               conf: scala.collection.mutable.Queue[FullSettings],
               state: scala.collection.mutable.Queue[ProgramState]): Unit = {

    // HURR HURR HURR
    StateClient.onStatePush   = (s => {say("state pushed"); state.enqueue(s)})
    StateClient.onConfPush    = (c => {say("conf pushed"); conf.enqueue(c)})
    AgentClient.onAgentUpdate = (a => {say(""); agent.enqueue(a)})
    WfClient.onWfUpdate       = (a => {say("got wf"); wf.enqueue(a)})

    Context.serverRpc.register
  }
}
