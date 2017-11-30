package cyborg

import fs2._
import fs2.Stream._

import cats.effect.Effect
import fs2.async.mutable.Queue
import scala.concurrent.ExecutionContext

import utilz._
import scala.concurrent.duration._

object Feedback {

  /**
    Creates a self modifying pipe

    createSimRunner:
    Should create one scenario to be evaluated,
    for instance, resetting the agent runner

    evaluator:
    Should emit exactly 1 evaluation per sim runner

    filterGenerator:
    A pipe that transforms evaluations into new filters.
    This pipe should keep some internal state.
    This pipe MUST emit a default pipe!!

    */
  def experimentPipe[F[_], ReservoirOutput, FilterOutput, O](
    createSimRunner: () => Pipe[F, FilterOutput, O], // resettable
    evaluator:             Pipe[F, O, Double],
    filterGenerator:       Pipe[F, Double, Pipe[F, ReservoirOutput, Option[FilterOutput]]]
  )(implicit ec: ExecutionContext, eff: Effect[F]): Pipe[F, ReservoirOutput, O] = { inStream =>

    type Filter = Pipe[F, ReservoirOutput, Option[FilterOutput]]

    /**
      Function that takes a filter, attaches it to the simulator
      and evaluates the output

      When input is run through the evaluator, two things happen:
      1: the reservoir data is interpreteded and used as input to the simRunner
      2: the performance of the filter is logged

      This pipe should terminate after running its course
      */
    def assembleEvaluator(filter: Filter, evalSink: Sink[F,Double])(implicit ec: EC): Pipe[F, ReservoirOutput, O] = {
      println("assembling an evaluator!")
      reservoirData =>
      reservoirData.through(filter).unNoneTerminate
        .through( createSimRunner() )
        .observe(_.through(evaluator).through(evalSink))
    }


    /**
      Dequeues one filter, runs it to completion and enqueues the evaluation
      */
    def loop(
      filterQueue:   Queue[F,Filter],
      inputQueue:    Queue[F,ReservoirOutput],
      evalSink:      Sink[F,Double]
    ): Stream[F,O] = {

      println("loop is running")

      Stream.eval(filterQueue.dequeue1) flatMap { filter =>

        val evaluator = assembleEvaluator(filter, evalSink)

        // TODO: Ideally we'd want dequeuBatch here, but this causes us to lose unconsumed values
        inputQueue.dequeue.through(evaluator) ++
          loop(filterQueue, inputQueue, evalSink)
      }
    }


    for {

      inputQueue      <- Stream.eval(fs2.async.boundedQueue[F,ReservoirOutput](100000))
      evaluationQueue <- Stream.eval(fs2.async.boundedQueue[F,Double](100))
      filterQueue     <- Stream.eval(fs2.async.boundedQueue[F,Filter](10))

      enqueueInput    = inStream.through(inputQueue.enqueue)
      evalSink        = (in: Stream[F,Double]) => in.through(evaluationQueue.enqueue)
      generateFilters = evaluationQueue.dequeue.through(filterGenerator).through(filterQueue.enqueue)


      output          <- loop(filterQueue, inputQueue, evalSink)
        .concurrently(enqueueInput)
        .concurrently(generateFilters)

    } yield (output)
  }
}
