package cyborg.backend.server

import cyborg._
import utilz._

import cyborg.backend.rpc.ServerRPCendpoint
import cyborg.shared.rpc.server.MainServerRPC
import cats.effect._

object ApplicationServer {

  case class RPCserver(s: org.eclipse.jetty.server.Server){
    def start: IO[Unit] = IO { s.start() }
    def stop: IO[Unit] = IO { s.stop() }
  }

  def assembleFrontend(implicit ec: EC): IO[RPCserver] = {

    import _root_.io.udash.rpc._
    import org.eclipse.jetty.server.Server
    import org.eclipse.jetty.server.session.SessionHandler
    import org.eclipse.jetty.servlet.{DefaultServlet, ServletContextHandler, ServletHolder}

    val port = 8080
    val resourceBase = "frontend/target/UdashStatics/WebContent"

    def createAtmosphereHolder()(implicit ec: EC) = {
      val config = new DefaultAtmosphereServiceConfig((clientId) =>
        new DefaultExposesServerRPC[MainServerRPC](
          new ServerRPCendpoint()(clientId, ec)
        )
      )

      val framework = new DefaultAtmosphereFramework(config)
      val atmosphereHolder = new ServletHolder(new RpcServlet(framework))
      atmosphereHolder.setAsyncSupported(true)
      atmosphereHolder
    }


    def createAppHolder() = {
      val appHolder = new ServletHolder(new DefaultServlet)
      appHolder.setAsyncSupported(true)
      appHolder.setInitParameter("resourceBase", resourceBase)
      appHolder
    }


    IO {
      say(s"Making a server with port: $port")
      val server = new Server(port)
      val contextHandler = new ServletContextHandler
      val appHolder = createAppHolder()
      val atmosphereHolder = createAtmosphereHolder()

      contextHandler.setSessionHandler(new SessionHandler)
      contextHandler.getSessionHandler.addEventListener(
        new org.atmosphere.cpr.SessionSupport()
      )

      contextHandler.addServlet(atmosphereHolder, "/atm/*")
      contextHandler.addServlet(appHolder, "/*")
      server.setHandler(contextHandler)

      RPCserver(server)
    }
  }
}
