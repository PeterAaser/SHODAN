package cyborg.frontend.services.rpc

import cyborg.shared.rpc.client.MainClientRPC

class RPCService extends MainClientRPC {

  override def pongPush(id: Int): Unit =
    println(s"pong push $id")

}


// class RPCService(notificationsCenter: NotificationsCenter) extends MainClientRPC {
//   override val chat: ChatNotificationsRPC =
//     new ChatService(notificationsCenter.msgListeners, notificationsCenter.connectionsListeners)
// }
