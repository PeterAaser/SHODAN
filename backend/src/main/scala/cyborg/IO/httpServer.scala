package cyborg

object HttpServer {
  import fs2._
  import fs2.async.mutable.Queue

  import org.http4s.server.middleware._
  import org.http4s._
  import org.http4s.dsl._
  import org.http4s.server.blaze.BlazeBuilder

  import cats.effect.IO

  import org.http4s.server.Server


  import HttpCommands._
  import DebugMessages._

  def SHODANservice(
    commands: Sink[IO,UserCommand],
    debugMessageQueue: Queue[IO,DebugMessage]): HttpService[IO] = {

    val debugMessagesAvailable = debugMessageQueue.size

    def cmd(command: UserCommand): IO[Unit] =
      Stream.emit(command).covary[IO].through(commands).run

    HttpService {
      case req @ POST -> Root / "connect" => {
        println("connect. WARNING DOES THE SAME AS STARTMEAME")
        for {
          emit <- cmd(StartMEAME)
          resp <- Ok("Connected")
        } yield (resp)
      }
      case req @ POST -> Root / "stop" => {
        println("stop")
        for {
          emit <- cmd(StopMEAME)
          resp <- Ok("Stopped")
        } yield (resp)
      }
      case req @ POST -> Root / "start" => {
        println("start")
        for {
          emit <- cmd(StartMEAME)
          resp <- Ok("start")
        } yield (resp)
      }

      case req @ POST -> Root / "db" => {
        println("db")
        for {
          emit <- cmd(RunFromDB(1))
          resp <- Ok("start")
        } yield (resp)
      }

      case req @ POST -> Root / "agent" => {
        println("agent")
        for {
          emit <- cmd(AgentStart)
          resp <- Ok("007 at your service")
        } yield (resp)
      }
      case req @ POST -> Root / "wf" => {
        println("waveform")
        for {
          emit <- cmd(StartWaveformVisualizer)
          resp <- Ok("what the fugg xD")
        } yield (resp)
      }

      case req @ GET -> Root / "info_waiting" => {
        println("got info waiting")
        for {
          available <- debugMessagesAvailable.get
          _ = println(s"asked for available, got $available")
          resp <- Ok(s"$available")
        } yield (resp)
      }

      case req @ GET -> Root / "info" => {
        for {
          message <- debugMessageQueue.dequeue1
          resp <- message match {
            case ChannelTraffic(_,a) => Ok(s"some info yo: ${a}")
            case _ => Ok("ayy lmao")
          }
        } yield (resp)
      }

      case req @ POST -> Root / "dspstimtest" => {
        for {
          emit <- cmd(DspStimTest)
          resp <- Ok("what the fugg xD")
        } yield (resp)
      }

      case req @ POST -> Root / "dspset" => {
        for {
          emit <- cmd(DspSet)
          resp <- Ok("what the fugg xD")
        } yield (resp)
      }


      case req @ POST -> Root / "dspuploadtest" => {
        for {
          emit <- cmd(DspUploadTest)
          resp <- Ok("what the fugg xD")
        } yield (resp)
      }

      case req @ POST -> Root / "fuckoff" => {
        for {
          emit <- cmd(Shutdown)
          resp <- Ok("shutting down")
        } yield (resp)
      }
    }
  }

  def SHODANserver(commands: Sink[IO,UserCommand], debugMessages: Queue[IO,DebugMessage]): IO[Server[IO]] = {
    val service = CORS(SHODANservice(commands, debugMessages))
    val builder = BlazeBuilder[IO].bindHttp(8080).mountService(service).start
    builder
  }
}

object HttpCommands {

  sealed trait UserCommand
  case object StartMEAME extends UserCommand
  case object StopMEAME extends UserCommand

  case object AgentStart extends UserCommand
  case object WfStart extends UserCommand
  case object StartWaveformVisualizer extends UserCommand

  case object ConfigureMEAME extends UserCommand

  case class RunFromDB(experimentId: Int) extends UserCommand
  case class StoreToDB(comment: String) extends UserCommand
  case object Shutdown extends UserCommand

  case object DspSet extends UserCommand
  case object DspConf extends UserCommand

  case object DspStimTest extends UserCommand
  case object DspUploadTest extends UserCommand // uploading stimulus, not bitfile
}


object DebugMessages {
  import cats.effect._

  import scala.concurrent.ExecutionContext

  import fs2._

  trait DebugMessage
  case class ChannelTraffic(name: Int, passed: Int) extends DebugMessage

  def attachDebugChannel[F[_]: Effect,I](
    msg: DebugMessage,
    passedPerMessage: Int,
    debugChannel: Sink[F, DebugMessage])(implicit ec: ExecutionContext): Pipe[F,I,I] = {

    val f = Stream.emit(msg).covary[F].through(debugChannel).run

    def go(s: Stream[F,_], counter: Int): Pull[F,Unit,Unit] = {
      s.pull.uncons flatMap {
        case Some((seg, tl)) => {
          if(seg.toVector.size+ counter > passedPerMessage){
            Pull.eval(f) >> go(tl, (seg.toVector.length + counter) % passedPerMessage)
          }
          else{
            go(tl, (seg.toVector.size + counter))
          }
        }
      }
    }

    in:Stream[F,I] => in.observeAsync(1000)(go(_:Stream[F,I], 0).stream)
  }
}
