package com.cyborg.rpc

import com.cyborg.wallAvoid.{ Agent, Coord }
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
  override def visualizer(): VisualizerRPC = new AgentServer
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

  backendExecutionContext.execute(
    new Runnable {
      override def run(): Unit = {
        var meme = 0
        while (true) {
          val msg = s"$meme"
          meme = meme + 1
          clients.synchronized {
            clients.foreach(clientId => {
                              ClientRPC(clientId).notifications().notify(msg)
                            })
          }
          TimeUnit.SECONDS.sleep(1)
        }
      }
    }
  )
}

class AgentServer(implicit clientId: ClientId) extends VisualizerRPC {

  override def registerAgent(): Future[Unit] = Future { println("agent ctrl registered"); AgentService.registerAgent }
  override def unregisterAgent(): Future[Unit] = Future {  println("agent ctrl unregistered"); AgentService.unregisterAgent }

}

object AgentService {

  import com.cyborg.Implicits.backendExecutionContext

  private val clients = scala.collection.mutable.ArrayBuffer[ClientId]()
  def registerAgent(implicit clientId: ClientId) = clients.synchronized {
    println(s"$clientId registered")
    clients += clientId
  }

  def unregisterAgent(implicit clientId: ClientId) = clients.synchronized {
    println(s"$clientId unregistered")
    clients -= clientId
  }

  backendExecutionContext.execute(
    new Runnable {
      override def run(): Unit = {
        var meme1 = 0.0
        var meme2 = 0.0
        var meme3 = 0.0
        while (true) {

          val agent: Agent =
            Agent(
              Coord(1000.0 + meme1, 1000.0 + meme2),
              meme3,
              120
            )

          println("Agent ctrl updating")

          meme1 = meme1 + 100.0
          meme2 = meme2 + 100.0
          meme3 = meme3 + 0.1
          if (meme3 > 3.14)
            meme3 = 0.0
          clients.synchronized {
            clients.foreach(
              clientId => {
                ClientRPC(clientId).visualizer().update(agent)
              }
            )
          }
          TimeUnit.SECONDS.sleep(1)
        }
      }
    }
  )
}
