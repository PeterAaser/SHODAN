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
  An experiment that queries a simple XOR

  The experiment happens as follows: Boolean inputs A and B are imposed as stimuli
  for some interval Δs (s for stim). The system is then given a rest Δr (r for rest) before stimuli is
  imposed at site C for some interval Δm (m for measure).

  Only during Δc is data collected to reduce inference from measurement and to test
  `short term memory` of reservoir dynamics

  Stim sites are as follows:

    B B # # A A
  B # # # # # # A
  B # C C C C # A
  _ # # # # # # #
  # # # # # # # #
  # # # # # # # #
  # # # # # # # #
    # # # # # #



  Let's see if we can't make this work with the closed loop system.
  The problem here is that the 'arrow' is swapped as the perturber has the first
  say.

  What we need is a Task pipe that simply ignores the first inputs (Δs + Δr).
  Likewise, the perturbator ignores the input from the task, its only job is to
  output a Boolean/Boolean pair

  TODO The experiment is performed for each XOR input in randomized order

  TODO We kinda want to flush the averaging after experiments are done, but they aint
  This could possibly be alleviated by not consuming input before we're ready?

  TODO Atm we're just using the agent defaults
  */
object XOR {

  type FilterOutput       = Chunk[Double]
  type ReadoutOutput      = Chunk[Double] // aka (Double, Double)
  type TaskOutput         = (Double, Double)
  type PerturbationOutput = Int


  // YOLO
  val deltaS = 1 second
  val deltaR = 20 millis
  val deltaM = 0.5 second

  val deltaSTicks = 100
  val deltaRTicks = 100
  val deltaMTicks = 100

  val challenges = Chunk((false, false), (false, true), (true, false), (true, true))

  lazy val challengesPerPipe       = hardcode(4)
  lazy val pipesPerGeneration      = hardcode(5)
  lazy val newPipesPerGeneration   = hardcode(2)
  lazy val newMutantsPerGeneration = hardcode(1)
  lazy val settings                = hardcode(Setting.FullSettings.default)
  lazy val readoutSettings         = hardcode(settings.filterSettings)
  lazy val pipesKeptPerGeneration  = pipesPerGeneration - (newPipesPerGeneration + newMutantsPerGeneration)



  // Task pipe really does nothing other than relay the readout layer
  def taskPipe[F[_]]: Pipe[F,ReadoutOutput,TaskOutput] = (s: Stream[F,ReadoutOutput]) => s.map(x => (.0,.0) )

  /**
    Next up we need a pipe that scores readoutlayers.

    The evaluator assumes that only valid input is received. It is up to the input filter
    to remove input from the stimuli phase
    */
  def evaluatorPipe[F[_]: Concurrent]: Pipe[F,TaskOutput,Double] = { inStream =>

    def evalSingle(a: Boolean, b: Boolean): Pipe[F,TaskOutput,Double] = { inStream =>
      inStream.foldMonoid.map{ case(x,y) =>
        if(a ^ b)
          x/(x+y) // prob true from 0 to 1
        else
          y/(x+y) // prob false from 0 to 1
      }
    }

    inStream.through(
      joinPipes(
        Stream.chunk(challenges)
          .map(Function.tupled(evalSingle))
          .covary[F]
      )
    ).foldMonoid
  }


  // TODO
  def createPerturbation(a: Boolean, b: Boolean): PerturbationOutput = {
    say("Using unimplemented method, danger danger")
    ???
  }


  /**
    The pipe for the perturbator is not really a pipe since it ignores the input from the RO.
    Instead it applies stimuli in a given pattern using a timer
    */
  def perturbator[F[_]: Timer]: Pipe[F,TaskOutput,PerturbationOutput] = { notUsed =>
    val t = implicitly[Timer[F]]

    /**
      Currently using placeholders for output.
      Starts stim, holds it for deltaS, stops it for deltaR then starts C (or not) for deltaM
      */
    def singlePerturb(a: Boolean, b: Boolean): Stream[F,PerturbationOutput] =
      (for {
         _ <- Pull.output1(1) // start stim
         _ <- Pull.eval(t.sleep(deltaS))
         _ <- Pull.output1(2) // start rest
         _ <- Pull.eval(t.sleep(deltaR))
         _ <- Pull.output1(3) // start C
         _ <- Pull.eval(t.sleep(deltaM))
       } yield ()).stream

    Stream.chunk(challenges)
      .map(Function.tupled(singlePerturb))
      .covary[F]
      .flatten
  }


  /**
    Readout layer generator taken from GA.scala. It's pretty crufty and possibly wrong.
    Connect it to a source of evaluations and you can pull some pipes out of it.
    */
  // Cribbed from GA.scala, hinting at a needed abstraction over filters
  def readoutLayerGenerator[F[_]]: Pipe[F, Double, Pipe[F,FilterOutput, ReadoutOutput]] = {

    import seqUtils._
    import Filters._
    import Filters.ANNPipes._
    import Genetics._

    def init(evalSource: Stream[F, Double]): Pull[F, Pipe[F,FilterOutput,ReadoutOutput], Unit] = {
      val initNetworks = (0 until pipesPerGeneration)
        .map(_ => (Filters.FeedForward.randomNetWithLayout(readoutSettings)))
        .toList

      val pipes: Chunk[Pipe[F,FilterOutput,ReadoutOutput]] = Chunk.seq(initNetworks.map(ffPipeC[F]))
      Pull.output(pipes) >> go(Chunk.seq(initNetworks), evalSource)
    }


    def go(previous: Chunk[FeedForward], evals: Stream[F, Double]): Pull[F, Pipe[F,FilterOutput,ReadoutOutput], Unit] = {
      evals.pull.unconsN((pipesPerGeneration - 1), false) flatMap {
        case Some((chunk, tl)) => {
          say("got evaluations")
          val scoredPipes = ScoredSeq(chunk.toVector.zip(previous.toVector))
          val nextPop = Chunk.seq(generate(scoredPipes))
          val nextPipes: Chunk[Pipe[F,FilterOutput,ReadoutOutput]]  = nextPop.map(ffPipeC)
          Pull.output(nextPipes) >> go(nextPop,tl)
        }
        case None => {
          say("uh oh")
          Pull.done
        }
      }
    }

    // Generates a new set of neural networks, no guarantees that they'll be any good...
    def generate(seed: ScoredSeq[FeedForward]): List[FeedForward] = {
      say("Generated pipes")
      val freak = mutate(seed.randomSample(1).repr.head._2)
      val rouletteScaled = seed.sort.rouletteScale
      val selectedParents = seed.randomSample(2)
      val (child1, child2) = fugg(selectedParents.repr.head._2, selectedParents.repr.tail.head._2)
      val newz = Vector(freak, child1, child2)
      val oldz = rouletteScaled.strip.takeRight(pipesKeptPerGeneration)

      (newz ++ oldz).toList
    }

    in => init(in).stream
  }




  /**
    Input source
    */
  def inputSource[F[_]](broadcastSource: List[Topic[F,TaggedSegment]]): Stream[F,Chunk[Double]] = {

    val spikeDetectorPipe = cyborg.spikeDetector.unsafeSpikeDetector[F](
      settings.experimentSettings.samplerate,
      settings.filterSettings.MAGIC_THRESHOLD) andThen (_.map(_.toDouble))

    demuxSegments(broadcastSource, spikeDetectorPipe, settings).map(Chunk.seq)
  }


  def perturbationSink[F[_]]: Sink[F,Int] = (s: Stream[F,Int]) => s.drain


  def XORExperiment[F[_]: Concurrent] = new ClosedLoopExperiment[
    F,
    FilterOutput,
    ReadoutOutput,
    TaskOutput,
    PerturbationOutput
  ]

  def huh[F[_]: Concurrent : Timer](inputs: List[Topic[F,TaggedSegment]]) = {

    val uhh = Stream.eval(Queue.bounded[F,Double](20)) flatMap { evalQueue =>

      val evaluationSink  = evalQueue.enqueue
      val readoutSource   = evalQueue.dequeue.through(readoutLayerGenerator)
      val performanceSink = evaluatorPipe andThen evaluationSink

      val exp: Sink[F,Chunk[Double]] = XORExperiment.run(
        taskPipe,
        perturbator,
        readoutSource,
        performanceSink,
        perturbationSink
      )
      inputSource(inputs).through(exp)
    }
  }
}
