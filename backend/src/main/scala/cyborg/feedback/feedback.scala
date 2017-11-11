package cyborg

import fs2._
import fs2.Stream._

import cats.effect.Effect
import fs2.async.mutable.Queue
import scala.concurrent.ExecutionContext

object Feedback {


  /**
    Creates a self modifying pipe, with some caveats
    */
  def experimentPipe[F[_]: Effect, ReservoirOutput, FilterOutput, O](
    createSimRunner: () => Pipe[F,FilterOutput, O], // resettable
    evaluator:             Pipe[F,O, Double],
    filterGenerator:       Pipe[F,Double, Pipe[F, ReservoirOutput, Option[FilterOutput]]]
  )(implicit ec: ExecutionContext): Pipe[F,ReservoirOutput, O] = { inStream =>

    type Filter = Pipe[F, ReservoirOutput, Option[FilterOutput]]

    /**
      Function that takes a filter, attaches it to the simulator
      and evaluates the output

      When input is run through the evaluator, two things happen:
      1: the reservoir data is interpreteded and used as input to the simRunner
      2: the performance of the filter is logged

      This pipe should terminate after running its course
      */
    def assembleEvaluator(filter: Filter, evalSink: Sink[F,Double]): Pipe[F, ReservoirOutput, O] = reservoirData =>
    reservoirData.through(filter).unNoneTerminate
      .through(createSimRunner() )
      .observe(_.through(evaluator).through(evalSink))


    /**
      Dequeues one filter, runs it to completion and enqueues the evaluation
      */
    def loop(
      filterQueue:   Queue[F,Pipe[F,ReservoirOutput,Option[FilterOutput]]],
      inputQueue:    Queue[F,ReservoirOutput],
      evalSink:      Sink[F,Double]
    ): Stream[F,O] = {

      Stream.eval(filterQueue.dequeue1) flatMap { filter =>

        val evaluator = assembleEvaluator(filter, evalSink)

        inputQueue.dequeueAvailable.through(evaluator) ++
          loop(filterQueue, inputQueue, evalSink)
      }
    }


    val inputQueueS = Stream.eval(fs2.async.boundedQueue[F,ReservoirOutput](100000))
    val evaluationQueueS = Stream.eval(fs2.async.boundedQueue[F,Double](100))
    val filterQueueS = Stream.eval(fs2.async.boundedQueue[F,Filter](10))

    inputQueueS.flatMap { inputQueue =>
      evaluationQueueS.flatMap { evaluationQueue =>
        filterQueueS.flatMap { filterQueue =>

          val enqueueInput = inStream.through(inputQueue.enqueue)
          val evalSink = evaluationQueue.enqueue
          val generateFilters = evaluationQueue.dequeue.through(filterGenerator).through(filterQueue.enqueue)

          loop(filterQueue, inputQueue, evalSink)
            .concurrently(enqueueInput)
            .concurrently(generateFilters)
        }
      }
    }
  }
}
