package cyborg.backend.rpc

////////////////////////////////////////
////////////////////////////////////////
////  BACKEND
////////////////////////////////////////

import cyborg.wallAvoid.Coord
import fs2.async.Ref
import io.udash.rpc._
import java.util.concurrent.TimeUnit
import scala.concurrent.Future

import cyborg._
import utilz._
import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.server.MainServerRPC

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


class ServerRPCendpoint(userQ: Queue[IO,ControlTokens.UserCommand],
                        wfListeners: Ref[IO,List[ClientId]],
                        agentListeners: Ref[IO,List[ClientId]])
                       (implicit ci: ClientId, ec: EC) extends MainServerRPC {

  implicit val coordCodec: GenCodec[Coord] = GenCodec.materialize
  implicit val agentCodec: GenCodec[Agent] = GenCodec.materialize

  override def ping(id: Int): Future[Int] = Future {
    TimeUnit.SECONDS.sleep(1)
    id
  }


  override def registerWaveform: Unit =
    wfListeners.modify(ci :: _).unsafeRunSync()

  override def unregisterWaveform: Unit =
    wfListeners.modify(_.filter(_ == ci)).unsafeRunSync()

  override def registerAgent: Unit =
    agentListeners.modify(ci :: _).unsafeRunSync()

  override def unregisterAgent: Unit =
    agentListeners.modify(_.filter(_ == ci)).unsafeRunSync()


  override def pingPush(id: Int): Unit = {
    val enq = userQ.enqueue1(ControlTokens.StopMEAME)
    val a = Agent(Coord(1.2, 1.3), 1.0, 100)
    ClientRPChandle(ci).wf().wfPush(Array(1,2,3))
    enq.unsafeRunAsync(_ => ())
    TimeUnit.SECONDS.sleep(1)
    ClientRPChandle(ci).pongPush(id)
  }
}
