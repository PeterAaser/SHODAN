package cyborg.backend.server

import cyborg._
import cyborg.RPCmessages._
import utilz._

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


  // Is it overkill to use conf effects here?? (yes, it is)
  def agentSink(listeners: Ref[IO, List[ClientId]], getConf: IO[FullSettings]): IO[Sink[IO,Agent]] = {

    def pushAgent(agent: Agent, listeners: Ref[IO, List[ClientId]]): IO[Unit] = {
      listeners.get.map(_.foreach( ClientRPChandle(_).agent().agentPush(agent) ))
    }

    def go(s: Stream[IO, Agent]): Pull[IO, Unit, Unit] = {
      s.pull.uncons1 flatMap {
        case Some((agent, tl)) => Pull.eval(pushAgent(agent, listeners)) >> go(tl)
        case None => Pull.done
      }
    }

    getConf map { _ =>
      in => go(in).stream
    }
  }



  def waveformSink(listeners: Ref[IO, List[ClientId]], getConf: IO[FullSettings]): IO[Sink[IO,TaggedSegment]] = {

    def sendData: Sink[IO,Array[Int]] = {
      def pushWaveforms(wf: Array[Int], listeners: Ref[IO, List[ClientId]]): IO[Unit] = {
        listeners.get.map(_.foreach( ClientRPChandle(_).wf().wfPush(wf) ))
      }

      def go(s: Stream[IO,Array[Int]]): Pull[IO,Unit,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((ts, tl)) => Pull.eval(pushWaveforms(ts, listeners)) >> go(tl)
          case None => Pull.done
        }
      }

      in => go(in).stream
    }

    import params.waveformVisualizer.pointsPerMessage

    getConf map { conf =>

      // seg len in = 2000, seg out = (200/10)*2 = 40
      val targetSegmentLength = hardcode(10)
      val samplerate = hardcode(20000)
      val segmentLength = hardcode(2000)

      in => in
        .map(_.downsampleHiLo(segmentLength/targetSegmentLength))
        .chunkify
        .through(mapN(targetSegmentLength*60*2, _.toArray)) // multiply by 2 since we're dealing with max/min
        .through(sendData)

    }
  }


  case class RPCserver(s: org.eclipse.jetty.server.Server){
    def start : IO[Unit] = IO { s.start() }
    def stop  : IO[Unit] = IO { s.stop()  }

    /**
      This could be a decent place to add topics, queues etc
      */
  }

  def assembleFrontend(
    userCommands      : Sink[IO,UserCommand],
    agentListeners    : Ref[IO,List[ClientId]],
    waveformListeners : Ref[IO,List[ClientId]],
    state             : Signal[IO,ProgramState],
    conf              : Signal[IO,FullSettings])
      : IO[RPCserver] = {

    import _root_.io.udash.rpc._
    import org.eclipse.jetty.server.Server
    import org.eclipse.jetty.server.session.SessionHandler
    import org.eclipse.jetty.servlet.{DefaultServlet, ServletContextHandler, ServletHolder}

    val port = 8080
    val resourceBase = "frontend/target/UdashStatics/WebContent"

    def createAtmosphereHolder(): ServletHolder = {
      val config = new DefaultAtmosphereServiceConfig((clientId) =>
        new DefaultExposesServerRPC[MainServerRPC](
          new ServerRPCendpoint(
            userCommands,
            agentListeners,
            waveformListeners)(
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
