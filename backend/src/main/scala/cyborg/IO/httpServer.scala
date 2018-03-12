package cyborg

import utilz._
import fs2._
import fs2.async.mutable.Queue

// import org.http4s.server.middleware._
// import org.http4s._
// import org.http4s.dsl.io._
// import org.http4s.server.blaze.BlazeBuilder

import cats.effect.IO

// import org.http4s.server.Server

object HttpServer {

//   import HttpCommands._
//   import DebugMessages._

//   def SHODANservice(
//     commands: Sink[IO,UserCommand],
//     debugMessageQueue: Queue[IO,DebugMessage]): HttpService[IO] = {


//     def cmd(command: UserCommand): IO[Unit] =
//       Stream.emit(command).covary[IO].through(commands).run

//     HttpService[IO] {
//       case POST -> Root / "connect" => {
//         for {
//           emit <- cmd(StartMEAME)
//           resp <- Ok("Connected")
//         } yield (resp)
//       }
//       case POST -> Root / "db" => {
//         say("db")
//         for {
//           emit <- cmd(RunFromDB(2))
//           resp <- Ok("start")
//         } yield (resp)
//       }
//       case POST -> Root / "dbNewest" => {
//         say("db")
//         for {
//           emit <- cmd(RunFromDBNewest)
//           resp <- Ok("start newest")
//         } yield (resp)
//       }
//       case POST -> Root / "agent" => {
//         say("agent")
//         for {
//           emit <- cmd(AgentStart)
//           resp <- Ok("007 at your service")
//         } yield (resp)
//       }
//       case POST -> Root / "fuckoff" => {
//         for {
//           emit <- cmd(Shutdown)
//           resp <- Ok("shutting down")
//         } yield (resp)
//       }
//       case POST -> Root / "record_start" => {
//         for {
//           emit <- cmd(DBstartRecord)
//           rest <- Ok("starting recording")
//         } yield (rest)
//       }
//       case POST -> Root / "record_stop" => {
//         for {
//           emit <- cmd(DBstopRecord)
//           rest <- Ok("starting recording")
//         } yield (rest)
//       }

//       case GET -> Root / "experiment_ids" => {
//         for {
//           ids  <- databaseIO.getAllExperimentIds
//           resp <- Ok(ids.toString)
//         } yield (resp)
//       }


// //////////////////////////////////////////////////////////
// //////////////////////////////////////////////////////////
// //////////////////////////////////////////////////////////
// // Debug stuff

//       case POST -> Root / "dspstimtest" => {
//         for {
//           emit <- cmd(DspStimTest)
//           resp <- Ok("what the fugg xD")
//         } yield (resp)
//       }

//       case POST -> Root / "dspuploadtest" => {
//         for {
//           emit <- cmd(DspUploadTest)
//           resp <- Ok("what the fugg xD")
//         } yield (resp)
//       }

//       case POST -> Root / "barf" => {
//         for {
//           emit <- cmd(DspBarf)
//           resp <- Ok("barfing")
//         } yield (resp)
//       }

//       case POST -> Root / "reset_dsp_debug" => {
//         for {
//           emit <- cmd(DspDebugReset)
//           resp <- Ok("resetting")
//         } yield (resp)
//       }

//       case POST -> Root / "test_stuff" => {
//         for {
//           emit <- cmd(DspStimTest)
//           resp <- Ok("hurr")
//         } yield (resp)
//         // for {
//         //   resp <- Ok("hurr")
//         //   _    <- IO { say("emitting run from MEAME...") }
//         //   emit <- cmd(StartMEAME)
//         //   _    <- IO { say("waiting 5 sec..."); Thread.sleep(5000) }
//         //   _    <- IO { say("emitting agent start") }
//         //   emit <- cmd(AgentStart)
//         //   _    <- IO { say("waiting 5 sec..."); Thread.sleep(5000) }
//         //   _    <- IO { say("emitting agent stop") }
//         //   emit <- cmd(AgentStop)
//         //   _    <- IO { say("waiting 5 sec..."); Thread.sleep(5000) }
//         //   _    <- IO { say("emitting run from DB") }
//         //   emit <- cmd(RunFromDB(1))
//         //   _    <- IO { say("waiting 10 sec..."); Thread.sleep(10000) }
//         //   _    <- IO { say("emitting run from MEAME") }
//         //   emit <- cmd(StartMEAME)
//         //   _    <- IO { say("waiting 10 sec..."); Thread.sleep(10000) }
//         //   _    <- IO { say("emitting agent start") }
//         //   emit <- cmd(AgentStart)
//         //   _    <- IO { say("waiting 10 sec..."); Thread.sleep(10000) }
//         //   _    <- IO { say("Okay, we're done?") }
//         // } yield (resp)
//       }
//     }
//   }

//   def SHODANserver(commands: Sink[IO,UserCommand], debugMessages: Queue[IO,DebugMessage]): IO[Server[IO]] = {
//     val service = CORS(SHODANservice(commands, debugMessages))
//     val builder = BlazeBuilder[IO].bindHttp(8080).mountService(service).start
//     builder
//   }
}

object HttpCommands {

  sealed trait UserCommand
  case object StartMEAME extends UserCommand
  case object StopMEAME  extends UserCommand

  case object StopData   extends UserCommand

  case object AgentStart extends UserCommand
  case object AgentStop  extends UserCommand

  // case object StartWaveformVisualizer extends UserCommand
  // case object ConfigureMEAME extends UserCommand

  case class  RunFromDB(experimentId: Int) extends UserCommand
  case object RunFromDBNewest              extends UserCommand
  case object DBstartRecord                extends UserCommand
  case object DBstopRecord                 extends UserCommand

  case object Shutdown                     extends UserCommand

  // Not that relevant now
  case object DspSet  extends UserCommand
  case object DspConf extends UserCommand

  case object DspStimTest   extends UserCommand
  case object DspUploadTest extends UserCommand // uploading stimulus, not bitfile
  case object DspBarf       extends UserCommand
  case object DspDebugReset extends UserCommand
}


// object DebugMessages {
//   import cats.effect._

//   import scala.concurrent.ExecutionContext

//   import fs2._

//   trait DebugMessage
//   case class ChannelTraffic(name: Int, passed: Int) extends DebugMessage

//   def attachDebugChannel[F[_]: Effect,I](
//     msg: DebugMessage,
//     passedPerMessage: Int,
//     debugChannel: Sink[F, DebugMessage])(implicit ec: ExecutionContext): Pipe[F,I,I] = {

//     val f = Stream.emit(msg).covary[F].through(debugChannel).compile.drain

//     def go(s: Stream[F,_], counter: Int): Pull[F,Unit,Unit] = {
//       s.pull.uncons flatMap {
//         case Some((seg, tl)) => {
//           if(seg.force.toVector.size+ counter > passedPerMessage){
//             Pull.eval(f) >> go(tl, (seg.force.toVector.length + counter) % passedPerMessage)
//           }
//           else{
//             go(tl, (seg.force.toVector.size + counter))
//           }
//         }
//       }
//     }

//     in:Stream[F,I] => in.observeAsync(1000)(go(_:Stream[F,I], 0).stream)
//   }
// }
