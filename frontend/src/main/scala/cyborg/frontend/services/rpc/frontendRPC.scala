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

  override def wfPush(data: Array[Int]): Unit = onWfUpdate(data)
  var onDrawCall: Array[Array[DrawCommand]] => Unit = (_: Array[Array[DrawCommand]]) => ()
  var onDrawCall2: Array[Array[DrawCommand]] => Unit = (_: Array[Array[DrawCommand]]) => ()
  var onDrawCall3: Array[Array[DrawCommand]] => Unit = (_: Array[Array[DrawCommand]]) => ()
  var onDrawCall4: Array[Array[DrawCommand]] => Unit = (_: Array[Array[DrawCommand]]) => ()
  override def dcPush(data: Array[Array[DrawCommand]]) = onDrawCall(data)
  override def dcPush2(data: Array[Array[DrawCommand]]) = onDrawCall2(data)
  override def dcPush3(data: Array[Array[DrawCommand]]) = onDrawCall3(data)
  override def dcPush4(data: Array[Array[DrawCommand]]) = onDrawCall4(data)
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
               state: scala.collection.mutable.Queue[ProgramState],
               drawCommands: scala.collection.mutable.Queue[Array[Array[DrawCommand]]],
               drawCommands2: scala.collection.mutable.Queue[Array[Array[DrawCommand]]],
               drawCommands3: scala.collection.mutable.Queue[Array[Array[DrawCommand]]],
               drawCommands4: scala.collection.mutable.Queue[Array[Array[DrawCommand]]],
  ): Unit = {

    // HURR HURR HURR
    StateClient.onStatePush   = (s => {say("state pushed"); state.enqueue(s)})
    StateClient.onConfPush    = (c => {say("conf pushed"); conf.enqueue(c)})
    AgentClient.onAgentUpdate = (a => {agent.enqueue(a)})
    WfClient.onWfUpdate       = (a => {wf.enqueue(a)})
    WfClient.onDrawCall       = (a => {drawCommands.enqueue(a)})
    WfClient.onDrawCall2      = (a => {drawCommands2.enqueue(a)})
    WfClient.onDrawCall3      = (a => {drawCommands3.enqueue(a)})
    WfClient.onDrawCall4      = (a => {drawCommands4.enqueue(a)})

    Context.serverRpc.register
  }
}
