package cyborg.shared.rpc.client

import cyborg.RPCmessages._
import cyborg.Settings._

import cyborg.State._

import cyborg.WallAvoid.{ Agent, Coord }
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}
import io.udash.rpc._

trait MainClientRPC {
  def wf(): WfClientRPC
  def agent(): AgentClientRPC
  def state(): ClientStateRPC
}

trait WfClientRPC {

  /**
    * The int is the destination canvas
    */
  def drawCallPush(data: (Int, Array[Array[DrawCommand]])): Unit
  def stimfreqPush(req: cyborg.StimReq): Unit
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
