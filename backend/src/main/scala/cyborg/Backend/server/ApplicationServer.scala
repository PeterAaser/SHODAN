package cyborg.backend.server

import cyborg._
import cyborg.RPCmessages._
import fs2.concurrent.SignallingRef
import utilz._
import State._

import fs2._
import cats.effect.concurrent.{ Ref }
import fs2.concurrent.{ Signal, Queue, Topic }

import cyborg.backend.rpc.ServerRPCendpoint
import cyborg.shared.rpc.server.MainServerRPC
import cats.effect._
import cyborg.wallAvoid.Agent
import _root_.io.udash.rpc._

import cyborg.backend.rpc.ClientRPChandle
import cyborg.Settings._

object ApplicationServer {


  case class RPCserver(s: org.eclipse.jetty.server.Server, listeners: Ref[IO, List[ClientId]]){

    def start : IO[Unit] = IO { s.start() }
    def stop  : IO[Unit] = IO { s.stop()  }

    def pushAgent(a: Agent, ci: List[ClientId]) : IO[Unit] =
      IO { ci.foreach(ClientRPChandle(_).agent().agentPush(a)) }
    def agentTap(a: Agent): IO[Unit] =
      listeners.get.flatMap(ci => pushAgent(a, ci))


    /**
      Yep, we cheat using IO.unit here
      */
    def waveformTap(data: Array[Int]): IO[Unit] = {
      listeners.get.flatMap{ci =>

        ci.foreach(ClientRPChandle(_).wf().wfPush(data))
        IO.unit
      }
    }
  }


  def assembleFrontend(
    userCommands      : Sink[IO,UserCommand],
    state             : SignallingRef[IO,ProgramState],
    conf              : SignallingRef[IO,FullSettings])
      : IO[RPCserver] = {

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
            conf)(
            clientId)
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

        RPCserver(server, ci)
      }
    }
  }
}
