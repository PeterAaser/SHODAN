package cyborg.backend.rpc

// import cyborg.backend.rpc.auth.AuthEndpoint
// import cyborg.backend.rpc.i18n.TranslationsEndpoint
// import cyborg.backend.rpc.secure.SecureEndpoint
// import cyborg.backend.services.DomainServices
// import cyborg.shared.model.auth.{UserContext, UserToken}
// import cyborg.shared.model.SharedExceptions
// import cyborg.shared.rpc.server.MainServerRPC
// import cyborg.shared.rpc.server.open.AuthRPC
// import cyborg.shared.rpc.server.secure.SecureRPC
// import io.udash.i18n.RemoteTranslationRPC
// import io.udash.rpc._

// class ExposedRpcInterfaces(implicit domainServices: DomainServices, clientId: ClientId) extends MainServerRPC {
//   // required domain services are implicitly passed to the endpoints
//   import domainServices._

//   private lazy val authEndpoint: AuthRPC = new AuthEndpoint

//   // it caches SecureEndpoint for a single UserToken (UserToken change is not an expected behaviour)
//   private var secureEndpointCache: Option[(UserToken, SecureEndpoint)] = None

//   private def secureEndpoint(implicit ctx: UserContext): SecureRPC = {
//     secureEndpointCache match {
//       case Some((token, endpoint)) if token == ctx.token =>
//         endpoint
//       case None =>
//         val endpoint = new SecureEndpoint
//         secureEndpointCache = Some((ctx.token, endpoint))
//         endpoint
//     }
//   }

//   override def auth(): AuthRPC = authEndpoint

//   override def secure(token: UserToken): SecureRPC = {
//     authService
//       .findUserCtx(token)
//       .map(ctx => secureEndpoint(ctx))
//       .getOrElse(throw SharedExceptions.UnauthorizedException())
//   }

//   override def translations(): RemoteTranslationRPC = TranslationsEndpoint
// }

////////////////////////////////////////
////////////////////////////////////////
////  BACKEND
////////////////////////////////////////

import io.udash.rpc._
import java.util.concurrent.TimeUnit
import scala.concurrent.Future

import cyborg._
import utilz._
import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.server.MainServerRPC

object ClientRPChandle {
  def apply(target: ClientRPCTarget)
           (implicit ec: EC): MainClientRPC =
    new DefaultClientRPC[MainClientRPC](target).get
}

class ServerRPCendpoint()(implicit ci: ClientId, ec: EC) extends MainServerRPC {
  override def ping(id: Int): Future[Int] = Future {
    TimeUnit.SECONDS.sleep(1)
    id
  }

  override def pingPush(id: Int): Unit = {
    say("crash")
    TimeUnit.SECONDS.sleep(1)
    ClientRPChandle(ci).pongPush(id)
  }

  // override def queryMeameState: Future[ProgramStateDescription] = ???

}
