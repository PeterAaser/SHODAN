package cyborg

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
  type TaskOutput         = Agent
  type PerturbationOutput = List[Double]

  lazy val challengesPerPipe       = hardcode(4)
  lazy val pipesPerGeneration      = hardcode(5)
  lazy val newPipesPerGeneration   = hardcode(2)
  lazy val newMutantsPerGeneration = hardcode(1)
  lazy val settings                = hardcode(Setting.FullSettings.default)
  lazy val readoutSettings         = hardcode(settings.filterSettings)
  lazy val pipesKeptPerGeneration  = pipesPerGeneration - (newPipesPerGeneration + newMutantsPerGeneration)
  lazy val gaSettings              = hardcode(Setting.FullSettings.default.gaSettings)
  lazy val filterSettings          = hardcode(Setting.FullSettings.default.filterSettings)

  lazy val ticksPerEval            = 1000

  def MazeRunner[F[_]: Concurrent] = new ClosedLoopExperiment[
    F,
    FilterOutput,
    ReadoutOutput,
    TaskOutput,
    PerturbationOutput
  ]


  /**
    Sets up a simulation running an agent in 5 different initial poses
    aka challenges.

    TODO use unconsLimit
    */
  def taskRunner[F[_]: Concurrent]: Pipe[F,ReadoutOutput,TaskOutput] = {

    val initAgent = {
      import params.game._
      Agent(Coord(( width/2.0), ( height/2.0)), 0.0, 90)
    }

    say("creating simrunner")
    def simRunner(agent: Agent): Pipe[F,FilterOutput, Agent] = {
      def go(ticks: Int, agent: Agent, s: Stream[F,FilterOutput]): Pull[F,Agent,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((agentInput, tl)) => {
            val nextAgent = Agent.updateAgent(agent, agentInput.toList)
            if (ticks > 0){
              Pull.output1(nextAgent) >> go(ticks - 1, nextAgent, tl)
            }
            else {
              say("Challenge done")
              Pull.output1(nextAgent) >> Pull.done
            }
          }
          case None => {
            say("simrunner done??")
            Pull.done
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
    Used to create readoutSource and evaluatorSink

    This is pretty crufty, and I'm not sure it's a very good evaluator at all

    Could be rewritten easily with foldMonoid
    */
  def taskEvaluator[F[_]: Concurrent]: Pipe[F,TaskOutput,Double] = {
    def go(s: Stream[F,Agent]): Pull[F,Double,Unit] = {
      s.pull.unconsN(ticksPerEval, false) flatMap {
        case Some((chunk, _)) => {
          val closest = chunk
            .map(_.distanceToClosest)
            .toList
            .min

          Pull.output1(closest) >> Pull.done
        }
        case None => {
          Pull.done
        }
      }
    }
    in => go(in).stream
  }


  /**
    It's a weakness that the perturbation transform doesn't actually do a real tf
    For experiment, maybe an adaptor should be mandatory?
    */
  def perturbationTransform[F[_]]: Pipe[F,TaskOutput,PerturbationOutput] = _.map(_.distances)


  def inputSource[F[_]](broadcastSource: List[Topic[F,TaggedSegment]]): Stream[F,Chunk[Double]] = {
    val threshold = hardcode(100)
    val spikeDetectorPipe = cyborg.spikeDetector.unsafeSpikeDetector[F](
      settings.experimentSettings.samplerate,
      threshold) andThen (_.map(_.toDouble))

    demuxSegments(broadcastSource, spikeDetectorPipe, settings).map(Chunk.seq)
  }


  /**
    Creates initial networks, then creates new ones based on how the inital networks performed
    and so on...
    */
  def readoutLayerGenerator[F[_]]: Pipe[F, Double, Pipe[F,FilterOutput, ReadoutOutput]] = { inStream =>

    val GArunner = new GArunner[F](gaSettings, filterSettings)
    val generator = GArunner.FFANNGenerator[F]

    inStream.through(generator).map(_.toPipe[F])
  }


  def runMazeRunner[F[_]: Concurrent : Timer](
    inputs: List[Topic[F,TaggedSegment]],
    perturbationSink: Sink[F,PerturbationOutput],
    agentSink: Sink[F,Agent])
      : Stream[F,Unit] = {

    Stream.eval(Queue.bounded[F,Double](20)) flatMap { evalQueue =>

      val readoutSource  = evalQueue.dequeue.through(readoutLayerGenerator)
      val evaluationSink = taskEvaluator andThen evalQueue.enqueue

      val taskRunnerObs  = taskRunner andThen (_.observeAsync(1000)(agentSink))

      val exp: Sink[F,Chunk[Double]] = MazeRunner.run(
        taskRunnerObs,
        perturbationTransform,
        readoutSource,
        evaluationSink,
        perturbationSink
      )
      inputSource(inputs).through(exp)
    }
  }
}
