package cyborg.shared.rpc.server

// import cyborg.shared.model.auth.UserToken
// import cyborg.shared.rpc.server.open.AuthRPC
// import cyborg.shared.rpc.server.secure.SecureRPC
// import io.udash.i18n._
// import io.udash.rpc._

// @RPC
// trait MainServerRPC {
//   /** Returns an RPC for authentication. */
//   def auth(): AuthRPC

//   /** Verifies provided UserToken and returns a [[cyborg.shared.rpc.server.secure.SecureRPC]] if the token is valid. */
//   def secure(token: UserToken): SecureRPC

//   /** Returns an RPC serving translations from the server resources. */
//   def translations(): RemoteTranslationRPC
// }

// object MainServerRPC extends DefaultServerUdashRPCFramework.RPCCompanion[MainServerRPC]

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
