package cyborg

import cats.effect._
import cats._
import cats.syntax._
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.{ Queue, Signal }
import fs2.io.tcp.Socket
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

object mockServer {

  import cyborg.DspRegisters
  import cyborg.HttpClient._
  import cyborg.utilz._

  case class DSPconfig(m: Map[Int, (Option[FiniteDuration], Boolean)])
  object DSPconfig {
    def init = DSPconfig( (0 to 2).map( x => (x, (None, false))).toMap)
  }

  implicit class DSPOps(d: DSPconfig) {
    def updatePeriod(groupIdx: Int, period: FiniteDuration): DSPconfig =
      d.copy(m = d.m.updated(groupIdx, (Some(period), d.m(groupIdx)._2)))
    def toggleGroup(groupIdx: Int, toggle: Boolean): DSPconfig =
      d.copy(m = d.m.updated(groupIdx, (d.m(groupIdx)._1, toggle)))

    def update(call: DspFuncCall): DSPconfig = {
      import cyborg.dsp.calls.DspCalls._
      call.func match {
        case SET_ELECTRODE_GROUP_PERIOD =>
          call.decodeSetPeriod.foldLeft(d){ case(_, (groupIdx, period)) =>
            d.updatePeriod(groupIdx, period) }

        case ENABLE_STIM_GROUP =>
          call.decodeToggleGroup.foldLeft(d){ case(_, (groupIdx, _)) =>
            d.toggleGroup(groupIdx, true) }

        case DISABLE_STIM_GROUP =>
          call.decodeToggleGroup.foldLeft(d){ case(_, (groupIdx, _)) =>
            d.toggleGroup(groupIdx, false) }

        case _ => d
      }
    }
  }

  case class ServerState(
    alive:           Boolean,
    running:         Boolean,
    dataAcquisition: Boolean,
    dspFlashed:      Boolean,
    daqParams:       Option[DAQparams],
    dspConfig:       DSPconfig
  )
  object ServerState {
    def init = ServerState(
      true,
      false,
      false,
      false,
      None,
      DSPconfig.init
    )
    def fucked = init.copy(alive = false)
  }


  def hello(s: Signal[IO,ServerState]) = HttpService[IO] {
    case GET -> Root => s.get flatMap { serverState =>
      if(!serverState.alive) InternalServerError()
      else Ok("")
    }
  }


  def DAQ(s: Signal[IO,ServerState]) = HttpService[IO] {
    case req @ POST -> Root / "connect" =>
      req.decode[DAQparams] { params =>
        s.modify(_.copy(running = true, daqParams = Some(params))).flatMap(_ => Ok(""))
      }
    case GET -> Root / "start" =>
      s.modify(_.copy(dataAcquisition = true)).flatMap(_ => Ok(""))
    case GET -> Root / "stop" =>
      s.modify(_.copy(dataAcquisition = false)).flatMap(_ => Ok(""))
  }


  def DSP(s: Signal[IO,ServerState]) = HttpService[IO] {
    case GET -> Root / "flash" =>
      s.modify(_.copy(dspFlashed = true)).flatMap(_ => Ok(""))
    case req @ POST -> Root / "call" => {
      req.decode[HttpClient.DspFuncCall] { data =>
        s.modify(cfg => cfg.copy(dspConfig = cfg.dspConfig.update(data))) >>
          Ok("")
      }
    }
    case req @ POST -> Root / "read" => {
      req.decode[DspRegisters.RegisterReadList] { data =>
        Fsay[IO](s"got dsp read request: $data") >> Ok("")
      }
    }
    case req @ POST -> Root / "write" => {
      req.decode[DspRegisters.RegisterSetList] { data =>
        Fsay[IO](s"got dsp write request: $data") >> Ok("")
      }
    }
  }


  // a frame is 60 segments, aka what MEAME2 deals with
  type Frame = Seq[Int]

  /**
    Loops through a database recording repeatedly.
    Whenever a socket is connected, the listeners ref should be updated
    */
  def broadcastDataStream(listeners: Queue[IO, Stream[IO, Socket[IO]]]): Stream[IO, Unit] = {

    // TODO take some recordings at different samplerates...
    def getStream(params: DAQparams): Stream[IO,Int] = params.samplerate match {
      case 1000 => ???
      case 5000 => ???
      case 10000 => ???
      case 20000 => ???
      case 25000 => ???
      case 40000 => ???
    }

    // Already throttled
    val fromDB = cyborg.io.sIO.DB.streamFromDatabaseThrottled(3).repeat
    val broadcast = Stream.eval(fs2.async.signalOf[IO,Frame](Nil)) flatMap { signal =>
      val dataIn = fromDB.through(utilz.vectorize(60))
        .through(_.map(_.map(_.data).flatten))
        .evalMap(signal.set(_))


      // TODO how to handle failure?
      def hoseData(socket: Socket[IO]): Stream[IO, Unit] =
        signal.discrete
          .through(chunkify)
          .through(intToBytes)
          .through(socket.writes(None))


      // Does this not clog?
      val attachSinks: Stream[IO, Unit] = listeners.dequeue.map(_.flatMap(hoseData))
        .joinUnbounded

      dataIn.concurrently(attachSinks)
    }
    broadcast
  }


  def tcpServer(listeners: Queue[IO, Stream[IO,Socket[IO]]])(implicit ev: Effect[IO]): Stream[IO, Unit] = {
    import backendImplicits._
    val createListener: Stream[IO, Unit] =
      fs2.io.tcp.server(new InetSocketAddress("0.0.0.0", params.TCP.port)).to(listeners.enqueue)

    createListener
  }


  def assembleTestHttpServer(port: Int): Stream[IO, Server[IO]] = {
    def mountServer(s: Signal[IO,ServerState])(implicit ev: Effect[IO]): IO[Server[IO]] = BlazeBuilder[IO]
      .bindHttp(port, "0.0.0.0")
      .mountService(hello(s), "/")
      .mountService(DAQ(s), "/DAQ")
      .mountService(DSP(s), "/DSP")
      .start

    for {
      meameState <- Stream.eval(fs2.async.signalOf[IO,ServerState](ServerState.init))
      server <- Stream.eval(mountServer(meameState))
    } yield server
  }


  def assembleTestTcpServer(port: Int): Stream[IO, Unit] = {
    for {
      listeners <- Stream.eval(fs2.async.boundedQueue[IO,Stream[IO,Socket[IO]]](10))
      _ <- Stream(tcpServer(listeners), broadcastDataStream(listeners)).joinUnbounded
    } yield ()
  }
}
