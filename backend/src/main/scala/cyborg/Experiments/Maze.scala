package cyborg

import cats.data.Kleisli
import cats._
import fs2._
import fs2.Stream._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.wallAvoid._
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


class Maze2[F[_]: Concurrent](conf: FullSettings){

  type FilterOutput       = Chunk[Double]
  type ReadoutOutput      = Chunk[Double] // aka (Double, Double)
  type TaskOutput         = Agent
  type PerturbationOutput = List[Double]

  lazy val ticksPerEval = hardcode(20)

  /**
    Sets up a single simulator run
    
    Maybe just have the task as an argument?
    Rather than as 
    */
  def taskRunner: Pipe[F,ReadoutOutput, TaskOutput] = {
    def simRunner(agent: Agent): Pipe[F,FilterOutput, Agent] = {
      def go(ticks: Int, agent: Agent, s: Stream[F,FilterOutput]): Pull[F,Agent,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((agentInput, tl)) => {
            val nextAgent = Agent.updateAgent(agent, agentInput.toList)
            if (ticks > 0){
              Pull.output1(nextAgent) >> go(ticks - 1, nextAgent, tl)
            }
            else {
              Pull.output1(nextAgent)
            }
          }
          case None => {
            Pull.doneWith("simRunner ded")
          }
        }
      }
      in => go(ticksPerEval, agent, in).stream
    }

    val challenges = wallAvoid.createChallenges
    val challengePipes = challenges.map(simRunner)

    joinPipes(Stream.emits(challengePipes).covary[F])
  }


  /**
    Very primitive, all it does is measure worst performance
    */
  def taskEvaluator: Pipe[F,Agent,Double] =
    _.map(_.distanceToClosest)
      .foldMonoid(bonus.minMonoid(.0))


  // There are many ways we can fix this. For instance we can just use a mutable buffer, then
  // as the stream is terminated just enqueue it, bypassing the spikeSink altogether.
  def run(
    inputStream      : Stream[F,FilterOutput],
    readoutLayer     : Pipe[F,FilterOutput,ReadoutOutput],
    enqueueDataset   : Chunk[Chunk[Double]] => F[Unit],
    perturbationSink : Pipe[F,TaskOutput,Unit]): F[Unit] = {
    val spikeBuf = scala.collection.mutable.ArrayBuffer[Chunk[Double]]()
      inputStream
        .map{x => spikeBuf.append(x); x}
        .through(readoutLayer)
        .through(taskRunner)
        .through(perturbationSink)
        .compile
        .drain >> Fsay[F]("Okay, one maze run is done") >> enqueueDataset((spikeBuf.toList.toChunk))
  }
}
