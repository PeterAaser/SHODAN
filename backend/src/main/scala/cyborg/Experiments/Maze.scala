package cyborg

import fs2._
import fs2.Stream._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.wallAvoid.Agent
import _root_.io.udash.rpc.ClientId
import java.nio.file.Paths
import org.joda.time.Seconds
import scala.Function1
import scala.language.higherKinds
import cats.effect._
import cats.implicits._

import cyborg.backend.server.ApplicationServer
import cyborg.Setting._
import cyborg.utilz._

import scala.concurrent.duration._

import backendImplicits._


/**
  I really really hope this one doesn't take more introduction...
  */
object Maze {

  type FilterOutput       = Chunk[Double]
  type ReadoutOutput      = Chunk[Double] // aka (Double, Double)
  type TaskOutput         = (Double, Double)
  type PerturbationOutput = Int



}




object GamePipe {

  import cats.effect.{ Concurrent }
  import scala.concurrent.ExecutionContext
  import scala.language.higherKinds

  import utilz._

  type ffANNinput  = Vector[Double]
  type ffANNoutput = List[Double]

  import fs2._
  import wallAvoid._
  import wallAvoid.Agent._

  val initAgent = {
    import params.game._
    Agent(Coord(( width/2.0), ( height/2.0)), 0.0, 90)
  }

  lazy val ticksPerEval = hardcode(1000)


  /**
    Interprets the output of the readout layer to run an agent for
    `ticksPerEval` ticks.
    */
  def agentRunner[F[_]](agent: Agent): Pipe[F,ffANNoutput,Agent] = {

    def go(ticks: Int, agent: Agent, s: Stream[F,ffANNoutput]): Pull[F,Agent,Unit] = {
      s.pull.uncons1 flatMap {
        case Some((agentInput, tl)) => {
          val nextAgent = Agent.updateAgent(agent, agentInput)
          if (ticks > 0){
            Pull.output1(nextAgent) >> go(ticks - 1, nextAgent, tl)
          }
          else {
            Pull.output1(nextAgent) >> Pull.done
          }
        }
        case _ => Pull.done
      }
    }
    in => go(ticksPerEval, agent, in).stream
  }


  /**
    Scores a single agent.

    TODO: Currently done with counting, but should ideally just be a simple
    transorm => foldMonoid which would not have to rely on ticksPerEval since
    foldMonoid only returns after input stream termination anyways.

    Would do right away, but I need to think about pipeJoin interactions first.
    */
  def evaluateRun[F[_]]: Pipe[F,Agent,Double] = {
    def go(s: Stream[F,Agent]): Pull[F,Double,Unit] = {
      s.pull.unconsN(ticksPerEval, false) flatMap {
        case Some((chunk, _)) => {
          val closest = chunk
            .map(_.distanceToClosest)
            .toList
            .min

          Pull.output1(closest) >> Pull.done
        }
        case _ => {
          Pull.done
        }
      }
    }
    in => go(in).stream
  }



}
