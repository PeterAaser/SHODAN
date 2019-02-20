package cyborg.shared.rpc.client

import cyborg.RPCmessages._
import cyborg.Settings._

import cyborg.State._

import cyborg.wallAvoid.{ Agent, Coord }
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}
import io.udash.rpc._

trait MainClientRPC {
  def wf(): WfClientRPC
  def agent(): AgentClientRPC
  def state(): ClientStateRPC
}

trait WfClientRPC {
  def wfPush(data: Array[Int]): Unit
  def dcPush(data: Array[Array[DrawCommand]])
}

trait AgentClientRPC {
  implicit val coordCodec: GenCodec[Coord] = GenCodec.materialize
  implicit val v: GenCodec[Agent] = GenCodec.materialize
  def agentPush(agent: Agent): Unit
}

trait ClientStateRPC {
  def pushConfig(c: FullSettings): Unit
  def pushState(s: ProgramState): Unit
}

object MainClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[MainClientRPC]
object WfClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[WfClientRPC]
object AgentClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[AgentClientRPC]
object ClientStateRPC extends DefaultClientUdashRPCFramework.RPCCompanion[ClientStateRPC]
