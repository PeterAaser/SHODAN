package com.cyborg.rpc

import io.udash.rpc._

import scala.concurrent.ExecutionContext

// Allows us to call client RPC with ClientRPC(allClients).doThing
object ClientRPC {
  def apply(target: ClientRPCTarget)(implicit ec: ExecutionContext): MainClientRPC = {
    new DefaultClientRPC[MainClientRPC](target).get
  }
}
