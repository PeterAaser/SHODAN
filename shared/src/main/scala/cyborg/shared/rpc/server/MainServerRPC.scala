package cyborg.shared.rpc.server

import io.udash.rpc._
import scala.concurrent.Future

import cyborg._
// import utilz._

// The methods the frontend can call from the backend
@RPC
trait MainServerRPC {
  def ping(id: Int): Future[Int]
  def pingPush(id: Int): Unit
  // def queryMeameState: Future[ProgramStateDescription]
}

object MainServerRPC extends DefaultServerUdashRPCFramework.RPCCompanion[MainServerRPC]
