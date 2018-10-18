package cyborg.shared.rpc.client

import cyborg._
import cyborg.RPCmessages._

import cyborg.wallAvoid.{ Agent, Coord }
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}

import io.udash.rpc._


// The methods the backend can call on the frontend
// There may be many frontend with various contexts, thus these methods may not return data
trait MainClientRPC {
  def wf(): WfClientRPC
  def agent(): AgentClientRPC
}

trait WfClientRPC {
  def wfPush(data: Array[Int]): Unit
}

trait AgentClientRPC {
  implicit val coordCodec: GenCodec[Coord] = GenCodec.materialize
  implicit val v: GenCodec[Agent] = GenCodec.materialize
  def agentPush(agent: Agent): Unit
}

object MainClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[MainClientRPC]
object WfClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[WfClientRPC]
object AgentClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[AgentClientRPC]
