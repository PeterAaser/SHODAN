package com.cyborg.rpc

import io.udash.rpc._
import java.util.concurrent.TimeUnit

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ExposedRpcInterfaces(implicit clientId: ClientId) extends MainServerRPC {
  override def hello(name: String): Future[String] =
    Future.successful(s"Hello, $name!")

  override def pushMe(): Unit =
    ClientRPC(clientId).push(42)

  override def ping(id: Int): Future[Int] = {
    TimeUnit.SECONDS.sleep(1)
    Future.successful(id)
  }

  override def notifications(): NotificationsServerRPC = new NotificationsServer
}

class NotificationsServer(implicit clientId: ClientId) extends NotificationsServerRPC {

  override def register(): Future[Unit] = Future { println("fugg?="); NotificationsService.register }

  override def unregister(): Future[Unit] = Future {  println("fugg!??"); NotificationsService.unregister }

}

object NotificationsService {

  println("Is this even being run?")
  import com.cyborg.Implicits.backendExecutionContext

  private val clients = scala.collection.mutable.ArrayBuffer[ClientId]()
  def register(implicit clientId: ClientId) = clients.synchronized {
    println(s"$clientId registered")
    clients += clientId
  }

  def unregister(implicit clientId: ClientId) = clients.synchronized {
    println(s"$clientId unregistered")
    clients -= clientId
  }

  backendExecutionContext.execute(new Runnable {
    override def run(): Unit = {
      var meme = 0
      while (true) {
        val msg = s"$meme"
        meme = meme + 1
        println("what the fug hre we go")
        clients.synchronized {
          clients.foreach(clientId => {
            ClientRPC(clientId).notifications().notify(msg)
          })
        }
        TimeUnit.SECONDS.sleep(1)
      }
    }
  })
}
