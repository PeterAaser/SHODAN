package cyborg.frontend.services.rpc

import cyborg.shared.rpc.client.MainClientRPC
import cyborg.wallAvoid.Agent

class RPCService extends MainClientRPC {

  override def pongPush(id: Int): Unit =
    println(s"pong push $id")

  override def wfPush(data: Array[Int]): Unit =
    println("got a heap of data")

  override def agentPush(agent: Agent): Unit =
    println(agent)

}


// class RPCService(notificationsCenter: NotificationsCenter) extends MainClientRPC {
//   override val chat: ChatNotificationsRPC =
//     new ChatService(notificationsCenter.msgListeners, notificationsCenter.connectionsListeners)
// }
