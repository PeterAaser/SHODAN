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
import Assemblers._
import cyborg.backend.server.ApplicationServer.RPCserver

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
    stateServer        : SignallingRef[IO,ProgramState],
    confServer         : Signal[IO,FullSettings],
    eventQueue         : Queue[IO,UserCommand],
    frontend           : RPCserver,
    httpClient         : MEAMEHttpClient[IO]
  ) : IO[Sink[IO,UserCommand]] = {

    for {
      actionRef <- SignallingRef[IO,ProgramActions](ProgramActions())
      topics    <- List.fill(60)(Topic[IO,TaggedSegment](TaggedSegment(-1, Chunk[Int]()))).sequence
    } yield {

      def startMEAME: IO[Unit] = {

        val interruptableAction = for {
          programState <- stateServer.get
          conf         <- confServer.get
          actions      <- startBroadcast(conf,
                                         programState,
                                         topics,
                                         _.evalMap(frontend.waveformTap(_)),
                                         httpClient)
        } yield actions

        val updateAndRun = for {
          action <- interruptableAction
          _      <- actionRef.update(state => (state.copy(stopData = action.interrupt)))
          _      <- action.start
        } yield ()

        updateAndRun
      }


      def go(s: Stream[IO,UserCommand]): Pull[IO,IO[Unit],Unit] = {
        s.pull.uncons1.flatMap{
          case None => Pull.done
          case Some((token, tl)) => token match {

            case Start => Pull.output1(startMEAME) >> go(tl)

            case Stop => ???
            case StartRecord => ???
            case StopRecord => ???

            case _ => ???
          }
        }
      }
      inStream => go(inStream).stream.map(Stream.eval).parJoinUnbounded
    }
  }
}
