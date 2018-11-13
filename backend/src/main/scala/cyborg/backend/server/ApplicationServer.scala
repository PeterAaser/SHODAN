package cyborg.backend.server

import cyborg._
import cyborg.RPCmessages._
import utilz._

import fs2._
import fs2.async.Ref
import fs2.async.mutable.Signal
import fs2.async.mutable.Queue
import fs2.async.mutable.Topic

import cyborg.backend.rpc.ServerRPCendpoint
import cyborg.shared.rpc.server.MainServerRPC
import cats.effect._
import cyborg.wallAvoid.Agent
import _root_.io.udash.rpc._

import cyborg.backend.rpc.ClientRPChandle

object ApplicationServer {


  // Is it overkill to use conf effects here?? (yes, it is)
  def agentSink(listeners: Ref[IO, List[ClientId]], getConf: IO[Setting.FullSettings])(implicit ec: EC): IO[Sink[IO,Agent]] = {

    def pushAgent(agent: Agent, listeners: Ref[IO, List[ClientId]])(implicit ec: EC): IO[Unit] = {
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


  def waveformSink(listeners: Ref[IO, List[ClientId]], getConf: IO[Setting.FullSettings])(implicit ec: EC): IO[Sink[IO,TaggedSegment]] = {

    def sendData: Sink[IO,Array[Int]] = {
      def pushWaveforms(wf: Array[Int], listeners: Ref[IO, List[ClientId]])(implicit ec: EC): IO[Unit] = {
        listeners.get.map(_.foreach( ClientRPChandle(_).wf().wfPush(wf) ))
      }

      def go(s: Stream[IO,Array[Int]]): Pull[IO,Unit,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((ts, tl)) => Pull.eval(pushWaveforms(ts, listeners)) >> go(tl)
          case None => Pull.done
        }
      }

      go(_).stream
    }

    import params.waveformVisualizer.pointsPerMessage

    getConf map { conf =>

      val pointsPerSec = conf.experimentSettings.samplerate
      val pointsNeededPerSec = params.waveformVisualizer.vizLength
      val segmentsPerSec = pointsPerSec/conf.experimentSettings.segmentLength
      val pointsNeededPerSegment = pointsNeededPerSec/segmentsPerSec

      val downsampledSegmentLength = (conf.experimentSettings.segmentLength*pointsNeededPerSec)/pointsPerSec

      val targetSegmentLength = 200/40

      val downsampler = downsamplePipe[IO,Int](pointsPerSec, pointsNeededPerSec)

      in => in.through(_.map(_.data)).through(chunkify)
        .through(downsampler)
        .through(modifySegmentLengthGCD(downsampledSegmentLength, targetSegmentLength))
        .through(mapN(targetSegmentLength*60, _.force.toArray))
        .through(sendData)

    }
  }


  case class RPCserver(s: org.eclipse.jetty.server.Server){
    def start: IO[Unit] = IO { s.start() }
    def stop: IO[Unit] = IO { s.stop() }
  }

  def assembleFrontend(
    userQ             : Queue[IO,UserCommand],
    agent             : Stream[IO,Agent],
    waveForms         : Topic[IO,TaggedSegment],
    agentListeners    : Ref[IO,List[ClientId]],
    waveformListeners : Ref[IO,List[ClientId]],
    state             : Signal[IO,ProgramState],
    conf              : Signal[IO,Setting.FullSettings],
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
          new ServerRPCendpoint(
            userQ,
            agentListeners,
            waveformListeners)(
            clientId, ec)
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
