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

  def doNothing(x: (Int, List[List[DrawCommand]])): Unit = ()
  var onDrawCall: ((Int, List[List[DrawCommand]])) => Unit = doNothing
  override def drawCallPush(data: (Int, List[List[DrawCommand]])) = onDrawCall(data)
}

object AgentClient extends AgentClientRPC {

  var onAgentUpdate: Agent => Unit = (_: Agent) => ()
  var agentRunning = false

  override def agentPush(agent: Agent): Unit = onAgentUpdate(agent)
}


object Hurr {
  def register(agent         : scala.collection.mutable.Queue[Agent],
               conf          : scala.collection.mutable.Queue[FullSettings],
               state         : scala.collection.mutable.Queue[ProgramState],
               drawCallDemux : (Int, Array[Array[DrawCommand]]) => Unit)
      : Unit = {

    // HURR HURR HURR
    StateClient.onStatePush   = (s => {say("state pushed"); state.enqueue(s)})
    StateClient.onConfPush    = (c => {say("conf pushed"); conf.enqueue(c)})
    AgentClient.onAgentUpdate = (a => {agent.enqueue(a)})
    WfClient.onDrawCall       = (a: (Int, List[List[DrawCommand]])) => drawCallDemux(a._1, a._2.map(_.toArray).toArray)

    Context.serverRpc.register
  }
}
