package cyborg.frontend

// import cyborg.frontend.routing.{LoginPageState, RoutingRegistryDef, RoutingState, StatesToViewFactoryDef}
// import cyborg.frontend.services.rpc.{NotificationsCenter, RPCService}
// import cyborg.frontend.services.{TranslationsService, UserContextService}
// import cyborg.shared.model.SharedExceptions
// import cyborg.shared.rpc.client.MainClientRPC
// import cyborg.shared.rpc.server.MainServerRPC
// import io.udash._
// import io.udash.rpc._

// object ApplicationContext {
//   import scala.concurrent.ExecutionContext.Implicits.global

//   private val routingRegistry = new RoutingRegistryDef
//   private val viewFactoryRegistry = new StatesToViewFactoryDef

//   val application = new Application[RoutingState](
//     routingRegistry, viewFactoryRegistry
//   )

//   application.onRoutingFailure {
//     case _: SharedExceptions.UnauthorizedException =>
//       // automatic redirection to LoginPage
//       application.goTo(LoginPageState)
//   }

//   val notificationsCenter: NotificationsCenter = new NotificationsCenter

//   // creates RPC connection to the server
//   val serverRpc: MainServerRPC = DefaultServerRPC[MainClientRPC, MainServerRPC](
//     new RPCService(notificationsCenter), exceptionsRegistry = new SharedExceptions
//   )
// }

import cyborg.frontend.services.rpc.RPCService
import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.server.MainServerRPC
import cyborg.frontend.routing._

import io.udash._
import io.udash.rpc.DefaultServerRPC

import cyborg._

object Context {
  private val routingRegistry = new RoutingRegistryDef
  private val viewFactoryRegistry = new StatesToViewFactoryDef
  implicit val applicationInstance = new Application[RoutingState](
    routingRegistry, viewFactoryRegistry
  )

  val serverRpc: MainServerRPC = DefaultServerRPC[MainClientRPC, MainServerRPC](
    new RPCService
      // You can also provide custom ExceptionCodecRegistry
      // and  RPC failure interceptors.
      //exceptionsRegistry = ???
      //rpcFailureInterceptors = ???
  )
}
