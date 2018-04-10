package cyborg.backend.rpc

////////////////////////////////////////
////////////////////////////////////////
////  BACKEND
////////////////////////////////////////

import cyborg.wallAvoid.Coord
import fs2.async.Ref
import fs2.async.mutable.Signal
import io.udash.rpc._
import java.util.concurrent.TimeUnit
import scala.concurrent.Future

import cyborg._
import utilz._
import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.server.MainServerRPC
import cyborg.RPCmessages._

import cats.effect._
import fs2._
import fs2.async.mutable.Queue
import fs2.async.mutable.Topic
import cyborg.wallAvoid.Agent
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}

import sharedImplicits._

object ClientRPChandle {
  def apply(target: ClientRPCTarget)
           (implicit ec: EC): MainClientRPC =
    new DefaultClientRPC[MainClientRPC](target).get
}


class ServerRPCendpoint(userQ: Queue[IO,UserCommand],
                        wfListeners: Ref[IO,List[ClientId]],
                        agentListeners: Ref[IO,List[ClientId]])
                       (implicit ci: ClientId, ec: EC) extends MainServerRPC {


  override def registerWaveform: Unit   = {
    say(s"registered $ci")
    wfListeners.modify(cis => (ci :: cis).toSet.toList).unsafeRunSync()
  }

  override def registerAgent: Unit      = agentListeners.modify(ci :: _).unsafeRunSync()

  override def unregisterWaveform: Unit = wfListeners.modify(_.filter(_ == ci)).unsafeRunSync()
  override def unregisterAgent: Unit    = agentListeners.modify(_.filter(_ == ci)).unsafeRunSync()

  override def getSHODANstate:  Future[EquipmentState] = {

    def accumulateError(errors: Either[List[EquipmentFailure], Unit])(error: Option[EquipmentFailure]): Either[List[EquipmentFailure], Unit] = {
      error match {
        case Some(err) => errors match {
          case Left(errorList) => Left(err :: errorList)
          case Right(_) => Left(List(err))
        }
        case None => errors
      }
    }

    val errors: Either[List[EquipmentFailure],Unit] = Right(())

    val hurr: IO[Either[List[EquipmentFailure],Unit]] = HttpClient.getMEAMEhealthCheck.map { s =>
      val durr: List[Option[EquipmentFailure]] = List(
        (if(s.isAlive)    None else Some(MEAMEoffline)),
        (if(s.dspAlive)   None else Some(DspDisconnected)),
        (if(!s.dspBroken) None else Some(DspBroken)))

      durr.foldLeft(errors){ case(acc,e) => accumulateError(acc)(e) }
    }
    val huh = hurr.attempt.map {
      case Left(_) => Left(List(MEAMEoffline))
      case Right(e) => e
    }
    say("yolo")
    huh.unsafeToFuture()
  }

  override def getRecordings: Future[List[RecordingInfo]] = databaseIO.getAllExperiments.unsafeToFuture()

  // TODO NYI
  override def startPlayback(recording: RecordingInfo): Unit = {
    // val thingy = for {
    //   // OK, maybe lenses aren't so bad...
    //   _ <- configuration.modify(c => c.copy( experiment = c.experiment.copy(samplerate = recording.samplerate, segmentLength = recording.segmentLength)))
    //   _ <- userQ.enqueue1( RunFromDB(recording.id) )
    // } yield()

    // thingy.unsafeRunSync()
    ???
  }


  import DspTests._
  val tests = List(
    stimulusTest1,
    stimulusTest2,
    stimulusTest3,
    oscilloscopeTest1,
    oscilloscopeTest2,
    oscilloscopeTest3)

  // lol no error handling
  override def runDspTest(testNo: Int): Unit = {
    tests(testNo).unsafeRunSync()
  }

  import DspCalls._
  override def stopDspTest: Unit = {
    stopStimQueue.unsafeRunSync()
  }
}
