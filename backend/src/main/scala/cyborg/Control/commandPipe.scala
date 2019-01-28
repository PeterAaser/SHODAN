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
    eventQueue         : Queue[IO,UserCommand])
      : IO[Sink[IO,UserCommand]] = {

    def go(s: Stream[IO,UserCommand]): Pull[IO,Stream[IO,Unit],Unit] = {
      s.pull.uncons1.flatMap{
        case None => Pull.done
        case Some((token, tl)) => token match {
          case ShoutAtOla => Pull.output1(Stream.eval( IO { say("thE fUCiNg dSp iS BroKeN agAiN") } )) >> go(tl)
          case Start => ???
          case Stop => ???
          case StartRecord => ???
          case StopRecord => ???
        }
      }
    }


    ???
  }
}
