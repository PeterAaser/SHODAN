package cyborg.backend.rpc

////////////////////////////////////////
////////////////////////////////////////
////  BACKEND
////////////////////////////////////////

import cyborg.wallAvoid.Coord
import cats.effect.concurrent.{ Ref, Deferred }
import fs2.concurrent.{ Signal, Queue, Topic }
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

object ClientRPChandle {
  def apply(target: ClientRPCTarget)
           (implicit ec: EC): MainClientRPC =
    new DefaultClientRPC[MainClientRPC](target).get
}


class ServerRPCendpoint(userQ: Queue[IO,UserCommand],
                        wfListeners: Ref[IO,List[ClientId]],
                        agentListeners: Ref[IO,List[ClientId]])
                       (implicit ci: ClientId, ec: EC) extends MainServerRPC {


  override def registerWaveform   : Unit = wfListeners.update(listeners => (ci :: listeners).toSet.toList).unsafeRunSync()
  override def unregisterWaveform : Unit = wfListeners.update(_.filter(_ == ci)).unsafeRunSync()

  override def registerAgent      : Unit = agentListeners.update(listeners => (ci :: listeners).toSet.toList).unsafeRunSync()
  override def unregisterAgent    : Unit = agentListeners.update(_.filter(_ == ci)).unsafeRunSync()


  //TODO move to token?
  override def flashDsp: Unit = {
    val runit = for {
      _ <- cyborg.dsp.DSP.flashDSP
      _ <- HttpClient.getMEAMEhealthCheck
    } yield()
    runit.unsafeRunSync()
  }


  override def getSHODANstate: Future[EquipmentState] = {
    val action = for {
      promise <- Deferred[IO,EquipmentState]
      _       <- userQ.enqueue1(GetSHODANstate(promise))
      res     <- promise.get
    } yield (res)

    action.unsafeToFuture()
  }


  override def getRecordings: Future[List[RecordingInfo]] = {
    val action = for {
      promise <- Deferred[IO,List[RecordingInfo]]
      _       <- userQ.enqueue1(GetRecordings(promise))
      res     <- promise.get
    } yield (res)

    action.unsafeToFuture()
  }


  override def startPlayback(recording: RecordingInfo): Unit = {
    userQ.enqueue1(RunFromDB(recording)).unsafeRunSync()
  }

  override def startRecording : Unit = userQ.enqueue1(DBstartRecord).unsafeRunSync()
  override def stopRecording  : Unit = userQ.enqueue1(DBstopRecord).unsafeRunSync()
  override def startLive      : Unit = userQ.enqueue1(StartMEAME).unsafeRunSync()
  override def startAgent     : Unit = userQ.enqueue1(AgentStart).unsafeRunSync()


  import dsp.DSP._
  override def uploadSine(period: Int, amplitude: Int): Future[Unit] =
    Fsay[IO]("nyi").unsafeToFuture()

  override def uploadSquare(period: Int, amplitude: Int): Future[Unit] =
    Fsay[IO]("nyi").unsafeToFuture()

  override def stopDspTest: Unit =
    stopStimQueue.unsafeRunSync()

  override def runDspTestWithElectrodes(electrodes: List[Int]): Unit =
    say("deprecated and unimplemented")

  // override def readDspMemory(reads: DspRegisters.RegisterReadList): Future[DspRegisters.RegisterReadResponse] =
  //   HttpClient.DSP.readRegistersRequest(reads).unsafeToFuture()
}
