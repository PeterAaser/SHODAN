package cyborg.frontend.services.rpc

import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.client.WfClientRPC
import cyborg.wallAvoid.Agent
import scala.collection.mutable
import cyborg.frontend.Context

class RPCService extends MainClientRPC {
  override def pongPush(id: Int): Unit =
    println(s"pong push $id")

  override def agentPush(agent: Agent): Unit =
    println(agent)

  override def wf(): WfClientRPC = WfClient
}

object WfClient extends WfClientRPC {

  var onWfUpdate: Array[Int] => Unit = (_: Array[Int]) => ()
  var wfRunning = false

  override def wfPush(data: Array[Int]): Unit = onWfUpdate(data)

  // Kinda magical on the registering front
  def register(q: mutable.Queue[Array[Int]]): Unit = {
    onWfUpdate = (λq: Array[Int]) => q.enqueue(λq)
    wfRunning = true

    // TODO implement placeholder rpc
    // should register
    Context.serverRpc.ping(123)
  }

  def unregister(): Unit = {
    wfRunning = false

    // TODO implement placeholder rpc
    Context.serverRpc.ping(123)

    // should unregister
  }
}

