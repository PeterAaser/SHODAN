package cyborg.shared.rpc.client

// import cyborg.shared.rpc.client.chat.ChatNotificationsRPC
// import io.udash.rpc._

// @RPC
// trait MainClientRPC {
//   def chat(): ChatNotificationsRPC
// }

// object MainClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[MainClientRPC]


import io.udash.rpc._


// The methods the backend can call on the frontend
// There may be many frontend with various contexts, thus these methods may not return data
@RPC
trait MainClientRPC {
  def pongPush(id: Int): Unit
}
object MainClientRPC extends DefaultClientUdashRPCFramework.RPCCompanion[MainClientRPC]
