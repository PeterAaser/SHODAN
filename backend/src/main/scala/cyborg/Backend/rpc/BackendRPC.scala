package cyborg.backend.rpc

////////////////////////////////////////
////////////////////////////////////////
////  BACKEND
////////////////////////////////////////

import cyborg.wallAvoid.Coord
import cats.effect.concurrent.{ Ref, Deferred }
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import io.udash.rpc._
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration._

import cyborg._
import utilz._
import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.server.MainServerRPC
import cyborg.RPCmessages._

import cats.effect._
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
                        userQ: Sink[IO,UserCommand],
                        state: SignallingRef[IO,ProgramState],
                        conf: SignallingRef[IO,FullSettings])
                       (implicit ci: ClientId) extends MainServerRPC {


  override def register   : Unit = listeners.update(listeners => (ci :: listeners).toSet.toList).unsafeRunSync()
  override def unregister : Unit = listeners.update(_.filter(_ == ci)).unsafeRunSync()

  override def setSHODANstate(s: ProgramState)  : Future[Either[String, Unit]] = ???
  override def setSHODANconfig(c: FullSettings) : Future[Unit] = ???

  override def getRecordings: Future[List[RecordingInfo]] = ???

  override def startPlayback(recording: RecordingInfo): Unit = ???

  override def startRecording : Unit = ???
  override def stopRecording  : Unit = ???

  override def startAgent     : Unit = ???

}
