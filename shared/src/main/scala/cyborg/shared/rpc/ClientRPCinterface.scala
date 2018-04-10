
package cyborg.shared.rpc.client

import cyborg._
import cyborg.RPCmessages._

import cyborg.wallAvoid.{ Agent, Coord }
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}

import io.udash.rpc._


// The methods the backend can call on the frontend
// There may be many frontend with various contexts, thus these methods may not return data
@RPC
trait MainClientRPC {
  def wf(): WfClientRPC
  def agent(): AgentClientRPC
}


@RPC
trait WfClientRPC {
  def wfPush(data: Array[Int]): Unit
}


@RPC
trait AgentClientRPC {
  def agentPush(agent: Agent): Unit
}
