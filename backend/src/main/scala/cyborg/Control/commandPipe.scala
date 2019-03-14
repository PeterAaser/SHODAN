package cyborg

import cats.data.Kleisli
import cats.effect.concurrent.Deferred
import fs2.concurrent.{ Queue, Signal, Topic, SignallingRef }
import cats.effect.concurrent.{ Ref }

import _root_.io.udash.rpc.ClientId
import java.io.IOException

import RPCmessages._
import cyborg.wallAvoid.Agent
import fs2._
import Settings._

import cats.effect.IO
import cats.effect._
import cats._
import cats.implicits._

import wallAvoid.Agent
import utilz._
import utilz.TaggedSegment

import cyborg.io._
import DB._
import File._
import Network._
import State._

import backendImplicits._
// import cyborg.backend.server.ApplicationServer.RPCserver

object ControlPipe {

  /**
    Do we need these?
    */
  case class ProgramActions(
    stopAgent       : IO[Unit] = IO.unit,
    stopRecording   : IO[Unit] = IO.unit,
    stopData        : IO[Unit] = IO.unit,
  )

  import cats.kernel.Eq
  implicit val eqPS: Eq[ProgramState] = Eq.fromUniversalEquals

  val placeholder = IO.unit

  def controlPipe(
    assembler          : Assembler,
    stateServer        : SignallingRef[IO,ProgramState],
    confServer         : Signal[IO,FullSettings],
    eventQueue         : Queue[IO,UserCommand]
  ) : IO[Sink[IO,UserCommand]] = {

    for {
      actionRef <- SignallingRef[IO,ProgramActions](ProgramActions())
    } yield {

      /**
        May start a playback or live recording based on datasource
        */
      def start: IO[Unit] = {

        for {
          programState     <- stateServer.get
          conf             <- confServer.get
          broadcast        <- assembler.startBroadcast(programState)(conf)
          frontendBrodcast <- assembler.broadcastToFrontend(conf)
          mazeRunner       <- assembler.assembleMazeRunnerBasicReservoir(conf)
          _                <- actionRef.update(state => (state.copy(stopData = broadcast.stop >> frontendBrodcast.stop >> mazeRunner.stop)))
          _                <- (broadcast.start, frontendBrodcast.start, mazeRunner.start).parMapN((_,_,_) => ())
        } yield {}
      }

      def stop: IO[Unit] = {
        for {
          interrupt <- actionRef.get.map(_.stopData)
          _         <- interrupt
          _         <- actionRef.update(_.copy(stopData = IO.unit))
        } yield()
      }


      def startRecording: IO[Unit] = {
        say("NYI WARNING!!!", Console.RED)
        for {
          programState <- stateServer.get
          conf         <- confServer.get
          // recordAction <- io.database.databaseIO.streamToDatabase(rawTopic.subscribe(10000), "Just a test")(conf)
          // _            <- actionRef.update(_.copy(stopRecording = recordAction.interrupt))
          // _            <- recordAction.start
        } yield()
      }

      def stopRecording: IO[Unit] = {
        for {
          interrupt <- actionRef.get.map(_.stopRecording)
          _         <- interrupt
          _         <- actionRef.update(_.copy(stopRecording = IO.unit))
        } yield()
      }


      def go(s: Stream[IO,UserCommand]): Pull[IO,IO[Unit],Unit] = {
        s.pull.uncons1.flatMap{
          case None => Pull.done
          case Some((token, tl)) => {say(s"got token $token"); token match {

            case Start       => Pull.output1(start)          >> go(tl)
            case Stop        => Pull.output1(stop)           >> go(tl)
            case StartRecord => Pull.output1(startRecording) >> go(tl)
            case StopRecord  => Pull.output1(stopRecording)  >> go(tl)

            case _ => ???
          }
          }
        }
      }
      inStream => go(inStream).stream.map(Stream.eval).parJoinUnbounded
    }
  }
}
