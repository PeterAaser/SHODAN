package cyborg.shared.rpc.client

import cyborg._

import cyborg.wallAvoid.{ Agent, Coord }
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}

import io.udash.rpc._


// The methods the backend can call on the frontend
// There may be many frontend with various contexts, thus these methods may not return data
@RPC
trait MainClientRPC {
  def pongPush(id: Int): Unit
  def wfPush(data: Array[Int]): Unit
  def agentPush(agent: Agent): Unit
}

// object MainClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[MainClientRPC] {}
// object MainClientRPC {}
