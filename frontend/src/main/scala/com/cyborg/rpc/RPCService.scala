package com.cyborg.rpc

import com.cyborg.Context
import com.cyborg.wallAvoid.Agent
import io.udash.rpc._
import com.cyborg.rpc._
import scala.concurrent.Future

class RPCService extends MainClientRPC {
  override def push(number: Int): Unit =
    println(s"Push from server: $number")

  override def notifications(): NotificationsClientRPC = NotificationsClient
  override def visualizer(): ClientVisualizerRPC = VisualizerClient
}

/** Client implementation */
object NotificationsClient extends NotificationsClientRPC {

  import Context._

  private val listeners = scala.collection.mutable.ArrayBuffer[(String) => Any]()

  def registerListener(listener: (String) => Any): Future[Unit] = {
    println("registering now")
    listeners += listener
    // register for server notifications
    if (listeners.size == 1) serverRpc.notifications().register()
    else Future.successful(())
  }

  def unregisterListener(listener: (String) => Any): Future[Unit] = {
    listeners -= listener
    // unregister
    if (listeners.isEmpty) serverRpc.notifications().unregister()
    else Future.successful(())
  }

  override def notify(msg: String): Unit = {
    listeners.foreach(_(msg))
  }
}


object VisualizerClient extends ClientVisualizerRPC {

  import Context._

  private val listeners = scala.collection.mutable.ArrayBuffer[(Agent) => Any]()

  def registerListener(listener: (Agent) => Any): Future[Unit] = {
    println("registering now")
    listeners += listener
    // register for server notifications
    if (listeners.size == 1) serverRpc.visualizer().registerAgent()
    else Future.successful(())
  }

  def unregisterListener(listener: (Agent) => Any): Future[Unit] = {
    listeners -= listener
    // unregister
    if (listeners.isEmpty) serverRpc.visualizer().unregisterAgent()
    else Future.successful(())
  }

  override def update(agent: Agent): Unit = {
    listeners.foreach(_(agent))
  }
}

object MEAMEControlClient {

  import Context._

  def startMEAME: Future[Unit] = {
    println("crashing server now")
    serverRpc.MEAMEControl().start()
  }
}
