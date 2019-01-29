package cyborg

import cats.data.Chain
import cats.effect.{ ExitCode, _ }
import cats._
import cats.syntax._
import cats.implicits._
import fs2._
import cats.effect.concurrent.Ref
import fs2.concurrent.{ Queue, Signal, SignallingRef }
import fs2.io.tcp.Socket
import java.net.InetSocketAddress
import org.http4s.server.Router
import org.http4s._
import org.http4s.syntax.kleisli._
import org.http4s.HttpApp
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import cyborg.bonus._
import scala.concurrent.duration._

import cyborg.utilz._
import cyborg.MEAMEmessages._
import cyborg.bonus._

import org.http4s.HttpApp
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

object mockServer {

  import backendImplicits._
  import mockDSP._

  case class ServerState(
    alive:           Boolean,
    running:         Boolean,
    dataAcquisition: Boolean,
    dspFlashed:      Boolean,
    daqParams:       Option[DAQparams],
    dspState:        DSPstate
  )
  object ServerState {
    def init = ServerState(
      true,
      false,
      false,
      false,
      None,
      DSPstate.init
    )
    def fucked = init.copy(alive = false)
  }


  def hello(s: SignallingRef[IO,ServerState]) = HttpService[IO] {
    case GET -> Root => s.get flatMap { serverState =>
      if(!serverState.alive) InternalServerError()
      else Ok("")
    }
    case GET -> Root / "status" => {
      import org.http4s.circe._
      import _root_.io.circe.generic.auto._
      import _root_.io.circe.syntax._
      implicit val MEAMEstatusCodec = jsonOf[IO, MEAMEstatus]
      Ok(MEAMEhealth(true,true).asJson)
    }
  }


  def DAQ(s: SignallingRef[IO,ServerState]) = HttpService[IO] {
    case req @ POST -> Root / "connect" =>
      req.decode[DAQparams] { params =>
        Fsay[IO](s"got params $params") >>
        s.update(_.copy(running = true, daqParams = Some(params))).flatMap(_ => Ok(""))
      }
    case GET -> Root / "start" =>
      s.update(_.copy(dataAcquisition = true)).flatMap(_ => Ok(""))
    case GET -> Root / "stop" =>
      s.update(_.copy(dataAcquisition = false)).flatMap(_ => Ok(""))
  }


  // server
  def DSP(s: SignallingRef[IO,ServerState], dspMessages: Ref[IO, Chain[DspFuncCall]]) = HttpService[IO] {
    case GET -> Root / "flash" => {
      s.update(_.copy(dspFlashed = true)) >> Fsay[IO]("flashing OK") >> Ok("hur")
    }

    case req @ POST -> Root / "call" => {
      req.decode[DspFuncCall] { data =>
        s.get.flatMap{ state =>
          if (!state.dspFlashed) say("Possible error: DSP is not flashed")
          dspMessages.update(data +: _) >> Ok("")
        }
      }
    }

    case req @ POST -> Root / "read" => {
      req.decode[DspRegisters.RegisterReadList] { data =>
        // val resp = dspRegisterState.applySeq(data.addresses)
        Fsay[IO](s"got dsp read request: ${data.addresses.map(_.toHexString)}") >> Ok("")
      }
    }
    case req @ POST -> Root / "write" => {
      req.decode[DspRegisters.RegisterSetList] { data =>
        // (data.addresses zip data.values).foreach{ case(r,v) => dspRegisterState.update(r, v)}
        // Fsay[IO](s"got dsp write request: $data") >> Ok("")
        Ok("")
      }
    }
  }


  // a frame is 60 segments, aka what MEAME2 deals with
  type Frame = Chunk[Int]

  /**
    Loops through a database recording repeatedly.
    Whenever a socket is connected, the listeners ref should be updated
    */
  def broadcastDataStream(listeners: Queue[IO, Resource[IO, Socket[IO]]]): Stream[IO, Unit] = {

    // TODO take some recordings at different samplerates...
    def getStream(params: DAQparams): Stream[IO,Int] = params.samplerate match {
      case 1000 => ???
      case 5000 => ???
      case 10000 => ???
      case 20000 => ???
      case 25000 => ???
      case 40000 => ???
    }

    val recordingId = hardcode(10)

    // Already throttled
    val fromDB = cyborg.io.DB.streamFromDatabaseThrottled(recordingId).repeat
    val broadcast = Stream.eval(SignallingRef[IO,Frame](Chunk.empty)) flatMap { signal =>
      val dataIn = fromDB.vecN(60)
        .map(x => Chunk.concatInts(x.map(_.data)))
        .evalMap(signal.set(_))

      val dataInAsChannel = fromDB.vecN(60)
        .map( x => {
               val huh = x.mapWithIndex{ case (ts, idx) =>
                 ts.data.map(_ => idx)
               }
               Chunk.concatInts(huh)
             }
        )
        .evalMap(signal.set(_))


      // TODO how to handle failure?
      def hoseData(socket: Socket[IO]): Stream[IO, Unit] =
        signal.discrete
          .through(chunkify)
          .through(intToBytes)
          .through(socket.writes(None))


      // TODO was rough porting, might be buggy
      val attachSinks = listeners.dequeue
        .flatMap(x => Stream.resource(x))
        .map(s => hoseData(s))
        .parJoinUnbounded

      dataIn.concurrently(attachSinks)
      // dataInAsChannel.concurrently(attachSinks)
    }
    broadcast
  }


  def tcpServer(listeners: Queue[IO, Resource[IO,Socket[IO]]]): Stream[IO, Unit] = {
    val ay = implicitly[ConcurrentEffect[IO]]
    val createListener: Stream[IO, Unit] =
      fs2.io.tcp.server[IO](new InetSocketAddress("0.0.0.0", params.Network.tcpPort)).to(listeners.enqueue)

    createListener
  }


  def assembleTestHttpServer(port: Int, dspMessageSink: Sink[IO,mockDSP.Event]): IO[Unit] = {

    def mountServer(
      s: SignallingRef[IO,ServerState],
      dspMessages: Ref[IO, Chain[DspFuncCall]]): Stream[IO, ExitCode] = {

      val myApp: HttpApp[IO] =
        Router(
          "/" -> hello(s),
          "/DAQ" -> DAQ(s),
          "/DSP" -> DSP(s, dspMessages)
        ).orNotFound

      val huh: Stream[IO, ExitCode] = BlazeServerBuilder[IO]
        .bindHttp(port, "0.0.0.0")
        .withHttpApp(myApp)
        .serve

      huh
    }

    // #sledgang
    for {
      meameStatus <- SignallingRef[IO,ServerState](ServerState.init)
      dspMessages <- Ref.of[IO,Chain[DspFuncCall]](Chain.nil)
    } yield {
      mockDSP.startDSP(dspMessages, 100.millis).compile.drain.unsafeRunAsyncAndForget()
      mountServer(meameStatus, dspMessages).compile.drain.unsafeRunAsyncAndForget()
    }
  }


  def assembleTestTcpServer(port: Int): Stream[IO, Unit] = {
    for {
      listeners <- Stream.eval(Queue.bounded[IO,Resource[IO,Socket[IO]]](10))
      _ = say("TCP serverino starterino", Console.CYAN)
      _ <- Stream(tcpServer(listeners), broadcastDataStream(listeners)).parJoinUnbounded
    } yield ()
  }
}
