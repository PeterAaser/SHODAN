package cyborg.backend.server

import cyborg._
import fs2._
import fs2.async.mutable.Queue
import fs2.async.mutable.Topic
import utilz._

import cyborg.backend.rpc.ServerRPCendpoint
import cyborg.shared.rpc.server.MainServerRPC
import cats.effect._
import cyborg.wallAvoid.Agent

object ApplicationServer {


  def waveformSink: Sink[IO,TaggedSegment] = {

    def sendData: Sink[IO,Array[Int]] = {
      def go(s: Stream[IO,Array[Int]]): Pull[IO,Unit,Unit] = {
        s.pull.unconsN(100, false) flatMap {
          case Some((segment, tl)) => ???
          case None => Pull.done
        }
      }
      go(_).stream
    }

    in => in.through(_.map(_.data)).through(chunkify)
      .through(mapN(params.waveformVisualizer.blockSize, _.force.toArray.head)) // downsample
      .through(mapN(params.waveformVisualizer.wfMsgSize, _.force.toArray))
      .through(sendData)
  }


  case class RPCserver(s: org.eclipse.jetty.server.Server){
    def start: IO[Unit] = IO { s.start() }
    def stop: IO[Unit] = IO { s.stop() }
  }

  def assembleFrontend(
    userQ: Queue[IO,ControlTokens.UserCommand],
    agent: Stream[IO,Agent],
    waveForms: Topic[IO,TaggedSegment]
    )(implicit ec: EC): IO[RPCserver] = {

    import _root_.io.udash.rpc._
    import org.eclipse.jetty.server.Server
    import org.eclipse.jetty.server.session.SessionHandler
    import org.eclipse.jetty.servlet.{DefaultServlet, ServletContextHandler, ServletHolder}

    val port = 8080
    val resourceBase = "frontend/target/UdashStatics/WebContent"

    def createAtmosphereHolder()(implicit ec: EC) = {
      val config = new DefaultAtmosphereServiceConfig((clientId) =>
        new DefaultExposesServerRPC[MainServerRPC](
          new ServerRPCendpoint(userQ)(clientId, ec)
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
