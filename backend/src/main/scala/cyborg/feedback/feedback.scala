package cyborg

import cats.Functor
import cats.effect.Concurrent
import fs2._
import fs2.Stream._

import cats.effect.Effect
import cats._
import cats.syntax._
import cats.implicits._
import fs2.concurrent.Queue

import utilz._
import scala.concurrent.duration._

object Feedback {

  def experimentPipe[F[_]: Concurrent, ReservoirOutput, FilterOutput, O](
    simRunner       : Pipe[F,FilterOutput,O],
    evaluator       : Pipe[F, O, Double],
    filterGenerator : Pipe[F, Double, ReservoirOutput => FilterOutput])
      : F[Pipe[F, ReservoirOutput, O]] =
  {

    type RFilter = ReservoirOutput => FilterOutput

    /**
      Creates a pipe2 that consumes n inputs before collecting a new filter
      */
    def readoutLayer[F[_]](tickPerEval: Int)
        : Pipe2[F, ReservoirOutput, ReservoirOutput => FilterOutput, FilterOutput] =
    {


      def pullFromLeft(l: Stream[F,ReservoirOutput], r: Stream[F,RFilter], count: Int, f: RFilter)
          : Pull[F,FilterOutput,Unit] =
      {

        l.pull.uncons flatMap {
          case Some((chunk,ltl)) if(chunk.size < count) => {
            Pull.output(chunk.map(f)) >>
              pullFromLeft(ltl, r, count - chunk.size, f)
          }
          case Some((chunk,ltl)) => {
            val (use,next) = chunk.splitAt(count)
            Pull.output(use.map(f)) >>
              pullFromRight(ltl.cons(next), r)
          }
          case None => {
            say("pull left None")
            Pull.done
          }
        }
      }

      def pullFromRight(l: Stream[F,ReservoirOutput], r: Stream[F,RFilter])
          : Pull[F,FilterOutput,Unit] =
      {
        r.pull.uncons1 flatMap {
          case Some((f,rtl)) => {
            say("pull right done")
            pullFromLeft(l, rtl, tickPerEval, f)
          }
          case None => {
            say("Pull left None")
            Pull.done
          }
        }
      }

      (a,b) => pullFromRight(a,b).stream
    }

    val ticksPerPullRight = 1000 * 5

    // TODO Trenger vi ikke observe??
    Queue.unbounded[F, RFilter] flatMap { filterQueue =>
      Queue.unbounded[F, O] map { q2 =>
        (s: Stream[F,ReservoirOutput]) => {
          val repeatingSimRunner = Pipe.join(Stream(simRunner).repeat)
          val simOut        : Stream[F,O]    = readoutLayer(ticksPerPullRight)(s, filterQueue.dequeue).through(repeatingSimRunner)
          val evaluationEnq : Stream[F,Unit] = simOut.observeAsync(100)(q2.enqueue).through(evaluator).through(filterGenerator).map{x => say(x); x}.through(filterQueue.enqueue)
          q2.dequeue.concurrently(evaluationEnq)
        }
      }
    }
  }
}
