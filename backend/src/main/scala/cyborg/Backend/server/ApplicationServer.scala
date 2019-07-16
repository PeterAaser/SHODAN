package cyborg.backend.server

import cyborg._
import cyborg.RPCmessages._
import fs2.concurrent.SignallingRef
import utilz._
import State._

import fs2._
import cats.effect.concurrent.{ Ref }
import fs2.concurrent.{ Signal, Queue, Topic }
import cats.implicits._

import org.eclipse.jetty.server.{ Server => JettyServer }

import cyborg.backend.rpc.ServerRPCendpoint
import cyborg.shared.rpc.server.MainServerRPC
import cats.effect._
import cyborg.WallAvoid.Agent
import _root_.io.udash.rpc._

import cyborg.backend.rpc.ClientRPChandle
import cyborg.Settings._

import scala.concurrent.duration._
/**
  All comms from SHODAN --> MEAME is accessed here
  */
class RPCserver(
  s: JettyServer,
  listeners: Ref[IO, List[ClientId]]){

  def start : IO[Unit] = IO { s.start() }
  def stop  : IO[Unit] = IO { s.stop()  }

  def agentPush(a: Agent): IO[Unit] =
    listeners.get.flatMap{ci =>
      IO { ci.foreach(ClientRPChandle(_).agent().agentPush(a)) }
    }


  def drawCommandPush(idx: Int)(data: Array[Array[DrawCommand]]): IO[Unit] =
    listeners.get.flatMap{ci =>
      IO {
        ci.foreach(ClientRPChandle(_).wf().drawCallPush((idx, data))) }
    }


  def stimReqPush(req: StimReq): IO[Unit] =
    listeners.get.flatMap{ci =>
      IO { ci.foreach(ClientRPChandle(_).wf().stimfreqPush(req)) }
    }
}


object ApplicationServer {

  def assembleFrontend(
    userCommands    : Queue[IO,UserCommand],
    state           : SignallingRef[IO,ProgramState],
    conf            : SignallingRef[IO,FullSettings],
    vizServer       : SignallingRef[IO,VizState],
  ) : IO[RPCserver] = {

    import _root_.io.udash.rpc._
    import org.eclipse.jetty.server.Server
    import org.eclipse.jetty.server.session.SessionHandler
    import org.eclipse.jetty.servlet.{DefaultServlet, ServletContextHandler, ServletHolder}

    val port = 8080
    val resourceBase = "frontend/target/UdashStatics/WebContent"

    def createAtmosphereHolder(listeners: Ref[IO, List[ClientId]]): ServletHolder = {
      val config = new DefaultAtmosphereServiceConfig((clientId) =>
        new DefaultExposesServerRPC[MainServerRPC](
          new ServerRPCendpoint(
            listeners,
            userCommands,
            state,
            conf,
            vizServer,
          )(clientId)
        )
      )

      val framework = new DefaultAtmosphereFramework(config)
      val atmosphereHolder = new ServletHolder(new RpcServlet(framework))
      atmosphereHolder.setAsyncSupported(true)
      atmosphereHolder
    }


    def createAppHolder(): ServletHolder = {
      val appHolder = new ServletHolder(new DefaultServlet)
      appHolder.setAsyncSupported(true)
      appHolder.setInitParameter("resourceBase", resourceBase)
      appHolder
    }

    import backendImplicits._
    cats.effect.concurrent.Ref.of[IO, List[ClientId]](Nil).flatMap { ci =>
      IO {
        say(s"Making a server with port: $port")
        val server = new Server(port)
        val contextHandler = new ServletContextHandler
        val appHolder = createAppHolder()
        val atmosphereHolder = createAtmosphereHolder(ci)

        contextHandler.setSessionHandler(new SessionHandler)
        contextHandler.getSessionHandler.addEventListener(
          new org.atmosphere.cpr.SessionSupport()
        )

        contextHandler.addServlet(atmosphereHolder, "/atm/*")
        contextHandler.addServlet(appHolder, "/*")
        server.setHandler(contextHandler)

        new RPCserver(server, ci)
      }
    }
  }
}
