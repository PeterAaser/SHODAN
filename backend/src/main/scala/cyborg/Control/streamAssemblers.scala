package cyborg

import cats.data.Kleisli
import fs2._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.wallAvoid.Agent
import _root_.io.udash.rpc.ClientId
import java.nio.file.Paths
import org.joda.time.Seconds
import scala.language.higherKinds
import cats.effect.IO
import cats.effect._
import cats._
import cats.implicits._

import cyborg.backend.server.ApplicationServer
import cyborg.Settings._
import cyborg.utilz._

import scala.concurrent.duration._

import backendImplicits._
import RPCmessages._

object Assemblers {

  /**
    Assembles the necessary components to start SHODAN
    */
  def startSHODAN: Stream[IO, Unit] = {

    for {
      stateServer       <- Stream.eval(SignallingRef[IO,ProgramState](ProgramState()))
      configServer      <- Stream.eval(SignallingRef[IO,FullSettings](FullSettings.default))
      listeners         <- Stream.eval(Ref.of[IO,List[ClientId]](List[ClientId]()))
      commandQueue      <- Stream.eval(Queue.unbounded[IO,UserCommand])

      frontend          <- Stream.eval(cyborg.backend.server.ApplicationServer.assembleFrontend(
                                         commandQueue.enqueue,
                                         listeners,
                                         stateServer,
                                         configServer))

      _                 <- Stream.eval(frontend.start)


      commandPipe       <- Stream.eval(staging.commandPipe(
                                         stateServer,
                                         configServer,
                                         commandQueue))

      _                 <- Ssay[IO]("###### All systems go ######", Console.GREEN_B)
      _                 <- Ssay[IO]("###### All systems go ######", Console.GREEN_B)
      _                 <- Ssay[IO]("###### All systems go ######", Console.GREEN_B)

      _                 <- commandQueue.dequeue.through(commandPipe)
    } yield ()
  }


  def assembleDBplayback    : Kleisli[IO,FullSettings,List[Topic[IO,TaggedSegment]]] = ???
  def assembleLiveNeuroData : Kleisli[IO,FullSettings,List[Topic[IO,TaggedSegment]]] = ???

  def assembleMazeRunner(
    broadcastSource : List[Topic[IO,TaggedSegment]],
    agentTopic: Topic[IO,Agent])
      : ConfF[Id,Stream[IO,Unit]] = Kleisli{ conf =>

    val perturbationSink: Sink[IO,List[Double]] =
      _.through(PerturbationTransform.toStimReq())
        .to(cyborg.dsp.DSP.stimuliRequestSink(conf))

    val mazeRunner = Maze.runMazeRunner(broadcastSource, perturbationSink, agentTopic.publish)
    val gogo = mazeRunner(conf)
    gogo: Id[Stream[IO,Unit]]
  }


  /**
    Takes a multiplexed dataSource and a list of topics.
    Demultiplexes the data and publishes data to all channel topics.

    Returns a tuple of the stream and a cancel action

    Kinda getting some second thoughts about this being an interruptable action.
    Shouldn't interruption really happen by terminating the enclosing stream?
    Not set in stone, I'm not even sure how that would look, just thingken

    A thought is, what if the cancellation is lost, then the stream will never
    close until it fails on its own. Not nescessarily a bad thing, just a thought

    TODO: Deliberate on whether this needs to be an InterruptibleAction
    */
  def broadcastDataStream(
    source      : Stream[IO,TaggedSegment],
    topics      : List[Topic[IO,TaggedSegment]],
    rawSink     : Sink[IO,TaggedSegment]) : IO[InterruptableAction[IO]] = {

    val interrupted = SignallingRef[IO,Boolean](false)

    def publishSink(topics: List[Topic[IO,TaggedSegment]]): Sink[IO,TaggedSegment] = {
      val topicsV = topics.toVector
      def go(s: Stream[IO,TaggedSegment]): Pull[IO,Unit,Unit] = {
        s.pull.uncons1 flatMap {

          case Some((taggedSeg, tl)) => {
            val idx = taggedSeg.channel
            if(idx != -1){
              Pull.eval(topicsV(idx).publish1(taggedSeg)) >> go(tl)
            }
            else go(tl)
          }
          case None => Pull.done
        }
      }

      in => go(in).stream
    }

    interrupted.map { interruptSignal =>
      InterruptableAction(
        interruptSignal.set(true),
        source
          .interruptWhen(interruptSignal)
          .observe(rawSink)
          .through(publishSink(topics))
          .compile.drain
      )
    }
  }


  def assembleTopics: Stream[IO,Topic[IO,TaggedSegment]] =
    Stream.repeatEval(Topic[IO,TaggedSegment](TaggedSegment(-1,Chunk[Int]())))


  val dspEventSink = (s: Stream[IO,mockDSP.Event]) => s.drain
  def assembleMockServer(eventSink: Sink[IO,mockDSP.Event] = dspEventSink): Stream[IO,Unit] =
    (for {
       _ <- Stream.eval(mockServer.assembleTestHttpServer(params.http.MEAMEclient.port, dspEventSink))
       _ <- Ssay[IO]("mock server up")
     } yield ()
    ).concurrently(mockServer.assembleTestTcpServer(params.TCP.port))
}
