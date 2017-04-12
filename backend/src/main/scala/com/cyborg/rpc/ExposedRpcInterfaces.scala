package com.cyborg.rpc

import com.cyborg.{ mainLoop, neuroServer }
import com.cyborg.wallAvoid.Agent
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
  override def MEAMEControl(): MEAMEControlRPC = {println("making new memectrl"); new MEAMEControlServer}
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
          TimeUnit.SECONDS.sleep(100)
        }
      }
    }
  )
}

class AgentServer(implicit clientId: ClientId) extends VisualizerRPC {

  override def registerAgent(): Future[Unit] = Future {
    println("agent ctrl registered")
    AgentService.registerAgent
  }

  override def unregisterAgent(): Future[Unit] = Future {
    println("agent ctrl unregistered")
    AgentService.unregisterAgent
  }
}

object AgentService {

  private val clients = scala.collection.mutable.ArrayBuffer[ClientId]()
  def registerAgent(implicit clientId: ClientId) = clients.synchronized {
    println(s"$clientId registered")
    clients += clientId
  }

  def unregisterAgent(implicit clientId: ClientId) = clients.synchronized {
    println(s"$clientId unregistered")
    clients -= clientId
  }

  def agentUpdate(agent: Agent): Unit = {
    println("agent ctrl updating")
    clients.foreach(
      clientId => {
        ClientRPC(clientId).visualizer().update(agent)
      }
    )
  }
}


class MEAMEControlServer(implicit clientId: ClientId) extends MEAMEControlRPC {

  override def start(): Future[Unit] = {
    println("exploding")
    Future { println("Starting MEAME"); MEAMEControlService.gogo }
  }

}

object MEAMEControlService {

  import fs2._
  import fs2.Task

  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(8)

  def gogo(implicit clientId: ClientId): Unit = {
    println("making task")
    val meme = mainLoop.outerT
    println("running task")
    meme.unsafeRun
    println("OK OK")

  }
}
