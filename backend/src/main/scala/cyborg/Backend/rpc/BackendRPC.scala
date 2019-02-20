package cyborg.backend.rpc

import cyborg.wallAvoid.Coord
import cats.effect.concurrent.{ Ref, Deferred }
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import io.udash.rpc._
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration._

import cyborg._
import utilz._
import State._
import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.server.MainServerRPC
import cyborg.RPCmessages._

import cats.effect._
import cats.implicits._
import fs2._
import cyborg.wallAvoid.Agent
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}

import sharedImplicits._
import backendImplicits._
import cyborg.Settings._

object ClientRPChandle {
  def apply(target: ClientRPCTarget): MainClientRPC =
    new DefaultClientRPC[MainClientRPC](target).get
}

class ServerRPCendpoint(listeners: Ref[IO,List[ClientId]],
                        userQ: Queue[IO,UserCommand],
                        state: SignallingRef[IO,ProgramState],
                        conf: SignallingRef[IO,FullSettings])
                       (implicit ci: ClientId) extends MainServerRPC {


  override def register : Future[Unit] = {
    say(s"got new listener: $ci")
    val t = for {
      _ <- listeners.update(listeners => (ci :: listeners).toSet.toList)
      l <- listeners.get
      s <- state.get
      c <- conf.get
    } yield {
      ClientRPChandle(ci).state().pushState(s)
      ClientRPChandle(ci).state().pushConfig(c)
    }
    t.unsafeToFuture()
  }

  override def unregister: Unit = listeners.update(_.filter(_ != ci)).unsafeRunSync()

  override def setSHODANstate(s: ProgramState)  : Future[Unit] = {
    say(s"setting new state $s")
    state.set(s).unsafeToFuture()
  }

  override def setSHODANconfig(c: FullSettings) : Future[Unit] = conf.set(c).unsafeToFuture()

  override def getRecordings : Future[List[RecordingInfo]] = {
    cyborg.io.database.databaseIO.getAllExperimentIds
      .flatMap{ids =>
        ids.map(id => cyborg.io.database.databaseIO.getRecordingInfo(id)).sequence
      }
    .unsafeToFuture()
  }

  override def startAgent : Unit = ???
  override def selectLargeChannel(c: Int) : Future[Unit] = ???

  def printState = state.discrete.map{x => say(s"state is $x", Console.GREEN); x}.drain
  def printStateChanges = state.discrete.changes.map{x => say(s"state is $x", Console.RED); x}.drain

  (printState merge printStateChanges).compile.drain.unsafeRunAsyncAndForget()
  

  val startStop = state.discrete
    .changes
    .map(_.isRunning).changes
    .map{x => say(s"new running state is $x", Console.CYAN); x}
    .evalTap(x =>
    if(x) userQ.enqueue1(Start) else userQ.enqueue1(Stop))

  val recordStartStop = state.discrete.map(_.isRecording).changes.evalTap(x =>
    if(x) userQ.enqueue1(StartRecord) else userQ.enqueue1(StopRecord))



  val pushConfs = conf.discrete.changes.map{ c =>
    val cis = listeners.get.unsafeRunSync()
    cis.foreach(ci => ClientRPChandle(ci).state().pushConfig(c) )
  }

  val pushState = state.discrete.changes.map{ s =>
    val cis = listeners.get.unsafeRunSync()
    cis.foreach(ci => ClientRPChandle(ci).state().pushState(s) )
  }

  (startStop merge recordStartStop merge pushConfs merge pushState).compile.drain.unsafeRunAsyncAndForget()
}
