package cyborg

import cats.effect._
import cats._
import cats.syntax._
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.Signal
import fs2.io.tcp.Socket
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object mockServer {

  import cyborg.DspRegisters
  import cyborg.HttpClient._
  import cyborg.utilz._

  case class ServerState(
    running: Boolean,
    dataAcquisition: Boolean,
    dspFlashed: Boolean,
    )

  def hello(s: Signal[IO,ServerState]) = HttpService[IO] {
    case GET -> Root =>
      Ok(s"hello this is MEAME.")
  }


  def DAQ(s: Signal[IO,ServerState]) = HttpService[IO] {
    case POST -> Root / "connect" =>
      s.modify(_.copy(running = true)).flatMap(_ => Ok(""))
    case GET -> Root / "start" =>
      s.modify(_.copy(dataAcquisition = true)).flatMap(_ => Ok(""))
    case GET -> Root / "stop" =>
      s.modify(_.copy(dataAcquisition = false)).flatMap(_ => Ok(""))
  }


  def DSP(s: Signal[IO,ServerState]) = HttpService[IO] {
    case GET -> Root / "flash" =>
      s.modify(_.copy(dspFlashed = true)).flatMap(_ => Ok(""))
    case req @ POST -> Root / "call" => {
      req.decode[DspRegisters.RegisterSetList] { data =>
        Fsay[IO](s"got dsp call: $data") >> Ok("")
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
  def broadcastDataStream(listeners: Ref[IO, List[Socket[IO]]]): IO[Unit] = {

    // Already throttled
    val fromDB = cyborg.io.sIO.DB.streamFromDatabase(3).repeat
    val broadcast = Stream.eval(fs2.async.signalOf[IO,Frame](Nil)) flatMap { signal =>
      val dataIn = fromDB.through(utilz.vectorize(60))
        .through(_.map(_.map(_.data).flatten))
        .through(logEveryNth(100, _ => s"got data in"))
        .evalMap(signal.set(_))

      // Does this not clog?
      val dataOut: Stream[IO, Unit] = signal.discrete
        .through(logEveryNth(100, _ => s"writing to socket"))
        .evalMap{ frame =>
        listeners.get.flatMap{ x =>
          x.map{ socket =>
            socket.write(Chunk.seq(frame.flatMap(BigInt(_).toByteArray)))
          }.sequence_
        }
      }
      dataIn.concurrently(dataOut)
    }
    broadcast.compile.drain
  }


  def tcpServer(listeners: Ref[IO, List[Socket[IO]]])(implicit ev: Effect[IO]): IO[Unit] = {

    import backendImplicits._
    val createListener =
      fs2.io.tcp.server(new InetSocketAddress("0.0.0.0", params.TCP.port)).flatMap { sockets =>
        val socketListener: Stream[IO, Unit] = sockets.flatMap(s =>
          for {
            v <- Stream.eval(listeners.get)
            _ <- Stream.eval(listeners.setSync(s :: v))
          } yield ())

        socketListener
      }

    createListener.compile.drain
  }


  def assembleTestServer(port: Int): IO[Unit] = {
    def mountServer(s: Signal[IO,ServerState]) = BlazeBuilder[IO]
      .bindHttp(port, "0.0.0.0")
      .mountService(hello(s), "/")
      .mountService(DAQ(s), "/DAQ")
      .mountService(DSP(s), "/DSP")
      .serve.drain

    for {
      listeners <- fs2.async.Ref[IO,List[Socket[IO]]](Nil)
      meameState <- fs2.async.signalOf[IO,ServerState](ServerState(false,false,false))
      _ <- (Stream.eval(tcpServer(listeners))
              .concurrently(Stream.eval(broadcastDataStream(listeners)))
              .concurrently(mountServer(meameState))).compile.drain
    } yield ()
  }
}
