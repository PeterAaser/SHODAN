package com.cyborg.rpc

import com.cyborg.Context
import io.udash.rpc._
import com.cyborg.rpc._
import scala.concurrent.Future

class RPCService extends MainClientRPC {
  override def push(number: Int): Unit =
    println(s"Push from server: $number")

  override def notifications(): NotificationsClientRPC = NotificationsClient
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
