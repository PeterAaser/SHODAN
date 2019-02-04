package cyborg.frontend

import cyborg.frontend.services.rpc.RPCService
import cyborg.shared.rpc.client.MainClientRPC
import cyborg.shared.rpc.server.MainServerRPC
import cyborg.frontend.routing._

import io.udash._
import io.udash.rpc.DefaultServerRPC

import cyborg.sharedImplicits._

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
