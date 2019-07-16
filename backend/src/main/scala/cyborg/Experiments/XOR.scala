/**
  Currently taking a little break due to kleisli configs not being implemented.
  */
// package cyborg

// import fs2._
// import fs2.Stream._
// import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
// import cats.effect.implicits._
// import cats.effect.Timer
// import cats.effect.concurrent.{ Ref }

// import cyborg.WallAvoid.Agent
// import _root_.io.udash.rpc.ClientId
// import java.nio.file.Paths
// import org.joda.time.Seconds
// import scala.Function1
// import scala.language.higherKinds
// import cats.effect._
// import cats.implicits._

// import cyborg.backend.server.ApplicationServer
// import cyborg.Settings._
// import cyborg.utilz._

// import scala.concurrent.duration._

// import backendImplicits._


// /**
//   An experiment that queries a simple XOR

//   The experiment happens as follows: Boolean inputs A and B are imposed as stimuli
//   for some interval Δs (s for stim). The system is then given a rest Δr (r for rest) before stimuli is
//   imposed at site C for some interval Δm (m for measure).

//   Only during Δc is data collected to reduce inference from measurement and to test
//   `short term memory` of reservoir dynamics

//   Stim sites are as follows:

//     B B # # A A
//   B # # # # # # A
//   B # C C C C # A
//   _ # # # # # # #
//   # # # # # # # #
//   # # # # # # # #
//   # # # # # # # #
//     # # # # # #



//   Let's see if we can't make this work with the closed loop system.
//   The problem here is that the 'arrow' is swapped as the perturber has the first
//   say.

//   What we need is a Task pipe that simply ignores the first inputs (Δs + Δr).
//   Likewise, the perturbator ignores the input from the task, its only job is to
//   output a Boolean/Boolean pair

//   A more clever design for this case would be to make a new pipe for each of the 4 experiments
//   Maybe try that?

//   And why not let the task pipe handle perturbation?
//   Because then Eval dunno what to do unless we use Either...

//   If we want to do the thing where we reset four times we also need to change how the readoutSource
//   works (just duplacate 4 times, should be ez)

//   Nope, that would be incompatible with the way experimentRunner works
//   */
// object XOR {

//   type FilterOutput       = Chunk[Double]
//   type ReadoutOutput      = Chunk[Double] // aka (Double, Double)
//   type TaskOutput         = (Double, Double)

//   /** For electrode sets     A                        B                      C */
//   type PerturbationOutput = (Option[FiniteDuration], Option[FiniteDuration], Option[FiniteDuration])


//   // YOLO
//   val deltaS = 3.second
//   val deltaR = 3.second
//   val deltaM = 3.second

//   // val deltaS = 2 second
//   // val deltaR = 1 second
//   // val deltaM = 1 second

//   val challenges = Chunk((false, false), (false, true), (true, false), (true, true))

//   lazy val challengesPerPipe       = hardcode(4)
//   lazy val pipesPerGeneration      = hardcode(5)
//   lazy val newPipesPerGeneration   = hardcode(2)
//   lazy val newMutantsPerGeneration = hardcode(1)
//   lazy val settings                = hardcode(Settings.FullSettings.default)
//   lazy val readoutSettings         = hardcode(settings.filterSettings)
//   lazy val pipesKeptPerGeneration  = pipesPerGeneration - (newPipesPerGeneration + newMutantsPerGeneration)


//   /**
//     We know that the chunk is of size 2 since that's what the neural network outputs.
//     Since the task pipe doesn't actually do anything it should only pass the results through.
//     */
//   def taskRunner[F[_]]: Pipe[F,ReadoutOutput,TaskOutput] = (s: Stream[F,ReadoutOutput]) =>
//     s.map{x => (x(0), x(1)) }


//   /**
//     Next up we need a pipe that scores readoutlayers.

//     The evaluator assumes that only valid input is received. It is up to the input filter
//     to remove input from the stimuli phase

//     Since we use foldMonoid nothing is gonna happen until the perturbator has terminated
//     */
//   def taskEvaluator[F[_]: Concurrent]: Pipe[F,TaskOutput,Double] = { inStream =>

//     def evalSingle(a: Boolean, b: Boolean): Pipe[F,TaskOutput,Double] = { inStream =>
//         inStream.foldMonoid.map{ case(x,y) =>
//           if(a ^ b)
//             x/(x+y) // certainty of true from 0 to 1
//           else
//             y/(x+y) // certainty of false from 0 to 1
//       }.map{x => say("Outputted an eval", Console.GREEN_B); x}
//     }

//     val perturbator = joinPipes(
//         Stream.chunk(challenges)
//           .map(Function.tupled(evalSingle))
//           .covary[F]
//       )

//     inStream.through(perturbator).foldMonoid.map{x => say(s"From fold monoid we got $x", Console.CYAN); x}
//   }


//   /**
//     Issues stimuli, with true yielding 10Hz (or w/e the time indicates, comments lie)
//     */
//   def createPerturbation(a: Boolean, b: Boolean, c: Boolean): PerturbationOutput = {
//     if(c)
//       (None, None, Some(0.1 second))
//     else
//       ((if(a) Some(0.1 second) else None),
//        (if(b) Some(0.1 second) else None),
//        None)
//   }


//   /**
//     The pipe for the perturbator is not really a pipe since it ignores the input from the RO.
//     Instead it applies stimuli in a given pattern using a timer

//     In this case it's not a real transform since it does not use the input.
//     */
//   def perturbationTransform[F[_]: Timer : Concurrent]: Pipe[F,TaskOutput,PerturbationOutput] = { notUsed =>
//     val t = implicitly[Timer[F]]

//     /**
//       Currently using placeholders for output.
//       Starts stim, holds it for deltaS, stops it for deltaR then starts C (or not) for deltaM
//       */
//     def singlePerturb(a: Boolean, b: Boolean): Stream[F,PerturbationOutput] =
//       (for {
//          _ <- Pull.output1(createPerturbation(a, b, false)) // start stim
//          _ <- Pull.eval(t.sleep(deltaS))
//          _ <- Pull.output1(createPerturbation(false, false, false)) // start rest
//          _ <- Pull.eval(t.sleep(deltaR))
//          _ <- Pull.output1(createPerturbation(false, false, true)) // start C
//          _ <- Pull.eval(t.sleep(deltaM))
//        } yield ()).stream


//     /**
//       The perturbation stream is not really responsible for terminating, that is the
//       taskrunners job, hence we add Stream.never
//      */
//     val perturbationStream = Stream.chunk(challenges)
//       .map(Function.tupled(singlePerturb))
//       .covary[F]
//       .flatten ++ Stream.never

//     perturbationStream
//       .concurrently(notUsed.drain)

//   }


//   /**
//     Readout layer generator taken from GA.scala. It's pretty crufty and possibly wrong.
//     Connect it to a source of evaluations and you can pull some pipes out of it.

//     TODO Seems to be outputting the same pipe per experiment.scala, but that's tertiary
//     to actually get this thing up and running properly
//     */
//   def readoutLayerGenerator[F[_]]: Pipe[F, Double, Pipe[F,FilterOutput, ReadoutOutput]] = {

//     ???
//     // import seqUtils._
//     // import Genetics._

//     // def init(evalSource: Stream[F, Double]): Pull[F, Pipe[F,FilterOutput,ReadoutOutput], Unit] = {
//     //   val initNetworks = (0 until pipesPerGeneration)
//     //     .map(_ => (Filters.FeedForward.randomNetWithLayout(readoutSettings)))
//     //     .toList

//     //   val pipes: Chunk[Pipe[F,FilterOutput,ReadoutOutput]] = Chunk.seq(initNetworks.map(ffPipeC[F]))
//     //   Pull.output(pipes) >> go(Chunk.seq(initNetworks), evalSource)
//     // }


//     // def go(previous: Chunk[FeedForward], evals: Stream[F, Double]): Pull[F, Pipe[F,FilterOutput,ReadoutOutput], Unit] = {
//     //   evals.pull.unconsN((pipesPerGeneration - 1), false) flatMap {
//     //     case Some((chunk, tl)) => {
//     //       say("got evaluations")
//     //       val scoredPipes = ScoredSeq(chunk.toVector.zip(previous.toVector))
//     //       val nextPop = Chunk.seq(generate(scoredPipes))
//     //       val nextPipes: Chunk[Pipe[F,FilterOutput,ReadoutOutput]]  = nextPop.map(ffPipeC)
//     //       Pull.output(nextPipes) >> go(nextPop,tl)
//     //     }
//     //     case None => {
//     //       say("uh oh")
//     //       Pull.done
//     //     }
//     //   }
//     // }

//     // // Generates a new set of neural networks, no guarantees that they'll be any good...
//     // def generate(seed: ScoredSeq[FeedForward]): List[FeedForward] = {
//     //   say("Generated pipes")
//     //   val freak = mutate(seed.randomSample(1).repr.head._2)
//     //   val rouletteScaled = seed.sort.rouletteScale
//     //   val selectedParents = seed.randomSample(2)
//     //   val (child1, child2) = fugg(selectedParents.repr.head._2, selectedParents.repr.tail.head._2)
//     //   val newz = Vector(freak, child1, child2)
//     //   val oldz = rouletteScaled.strip.takeRight(pipesKeptPerGeneration)

//     //   (newz ++ oldz).toList
//     // }

//     // in => init(in).stream
//   }




//   /**
//     Input source
//     */
//   def inputSource[F[_]](broadcastSource: List[Topic[F,TaggedSegment]]): Stream[F,Chunk[Double]] = {

//     val threshold = hardcode(100)
//     val spikeDetectorPipe = cyborg.spikeDetector.unsafeSpikeDetector[F](
//       settings.experimentSettings.samplerate,
//       threshold) andThen (_.map(_.toDouble))

//     demuxSegments(broadcastSource, spikeDetectorPipe, settings).map(Chunk.seq)
//   }


//   def XORExperiment[F[_]: Concurrent] = new ClosedLoopExperiment[
//     F,
//     FilterOutput,
//     ReadoutOutput,
//     TaskOutput,
//     PerturbationOutput
//   ]

//   def runXOR[F[_]: Concurrent : Timer](
//     inputs: List[Topic[F,TaggedSegment]],
//     perturbationSink: Sink[F,PerturbationOutput])
//       : Stream[F,Unit] = {

//     Stream.eval(Queue.bounded[F,Double](20)) flatMap { evalQueue =>

//       val readoutSource   = evalQueue.dequeue.through(readoutLayerGenerator)
//       val evaluationSink = taskEvaluator andThen evalQueue.enqueue

//       val exp: Sink[F,Chunk[Double]] = XORExperiment.run(
//         taskRunner,
//         perturbationTransform,
//         readoutSource,
//         evaluationSink,
//         perturbationSink
//       )
//       inputSource(inputs).through(exp)
//     }
//   }
// }
