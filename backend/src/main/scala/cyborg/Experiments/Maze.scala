package cyborg

import cats.data.Kleisli
import cats._
import fs2._
import fs2.Stream._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.WallAvoid._
import java.nio.file.Paths
import org.joda.time.Seconds
import scala.Function1
import scala.language.higherKinds
import cats.effect._
import cats.implicits._

import cyborg.backend.server.ApplicationServer
import cyborg.Settings._
import cyborg.utilz._

import scala.concurrent.duration._

import backendImplicits._


class Maze[F[_]: Concurrent](conf: FullSettings){

  type FilterOutput       = Chunk[Double]
  type ReadoutOutput      = Chunk[Double] // aka (Double, Double)
  type TaskOutput         = Agent
  type PerturbationOutput = List[Double]

  import conf.optimizer._
  val ticksPerRun = ticksPerEval/2

  /**
    Sets up a single simulator run
    */
  def taskRunner: Pipe[F,ReadoutOutput, TaskOutput] = {

    def simRunner(agent: Agent): Pipe[F,FilterOutput, Agent] = {
      def go(ticksRemaining: Int, agent: Agent, s: Stream[F,FilterOutput]): Pull[F,Agent,Unit] = {
        s.pull.unconsLimit(ticksRemaining) flatMap {
          case Some((agentInputs, tl)) => {
            val (agents, nextAgent) = agentInputs.scanLeftCarry(agent){ case(agent, input) =>
              agent.update(input(0), input(1))
            }
            if (ticksRemaining - agentInputs.size == 0){
              Pull.output(agents)
            }
            else {
              Pull.output(agents) >> go(ticksRemaining - agentInputs.size, nextAgent, tl)
            }
          }
          case None => {
            Pull.doneWith("simRunner ded")
          }
        }
      }
      in => go(ticksPerRun, agent, in).stream
    }

    val challenges = WallAvoid.createChallenges
    val challengePipes = challenges.map(simRunner)

    joinPipes(Stream.emits(challengePipes).covary[F])
  }


  def run(
    inputStream      : Stream[F,FilterOutput],
    readoutLayer     : Pipe[F,FilterOutput,ReadoutOutput],
    perturbationSink : Pipe[F,TaskOutput,Unit])
      : F[Array[(Agent, FilterOutput)]] = {

    val spikeBuf = scala.collection.mutable.ArrayBuffer[Chunk[Double]]()
    val taskBuf = scala.collection.mutable.ArrayBuffer[Agent]()

    inputStream
      .map{x => spikeBuf.append(x); x}
      .through(readoutLayer)
      .through(taskRunner)
      .map{x => taskBuf.append(x); x}
      .through(perturbationSink)
      .compile
      .drain  >> Fsay[F]("")
      .as{
        val huh = (taskBuf.toList zip spikeBuf.toList).toArray
        huh
      }
  }
}
