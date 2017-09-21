package com.cyborg

import fs2._
import fs2.Stream._

import cats.effect.Effect
import scala.concurrent.ExecutionContext

object GApipes {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  import wallAvoid._
  import params.GA._

  /**
    Sets up the actual experiment. Relies on three queues:

    Input Queue: Neuro-data
    Eval Queue: Stores evaulations of ANNs
    Pipe Queue: Helps keeping track of which evaluations should be paired with which ANNs

    The experimentPipe has a sort of inner/outer structure, where the outer structure consists
    of book-keeping between the three queues while the inner does the actual evaluation and
    number crunching working with the supplied queues
    */
  def experimentPipe[F[_]: Effect](inStream: Stream[F,ffANNinput], layout: List[Int])(implicit ec: ExecutionContext): Stream[F,Agent] = {


    println("Creating GA experiment pipe")
    val inputQueueTask = fs2.async.boundedQueue[F,ffANNinput](100000)
    val evaluationQueueTask = fs2.async.boundedQueue[F,Double](12000)
    val networkQueueTask = fs2.async.boundedQueue[F,Pipe[F,ffANNinput,ffANNoutput]](12000)

    def loop(
      pipeStream: Stream[F,Pipe[F,ffANNinput,ffANNoutput]],
      inputStream: Stream[F,ffANNinput],
      evalSink: Sink[F,Double]
    ): Stream[F,Agent] =
    {

      println("Looping")


      /**
        maps pipestream, a stream[pipe[ffIn,ffOut]],
        to a stream[pipe[ffIn,Agent]] as well as attaching an evaluator
        */
      val evaluatingAgentPipeStream: Stream[F,Pipe[F,ffANNinput,Agent]] = {
        pipeStream.through{
          _.map {
            p: Pipe[F,ffANNinput,ffANNoutput] => {
              s: Stream[F,ffANNinput] => {
                s.through(p)
                  .through(agentPipe.evaluatorPipe(ticksPerEval, evalFunc, evalSink))
              }
            }
          }
        }
      }

      val evaluatingAgentPipe: Pipe[F,ffANNinput,Agent] = Pipe.join(evaluatingAgentPipeStream)


      // We run the input through the consolidated pipe
      val flow = inputStream.through(evaluatingAgentPipe)


      // YOLO :--DDDd
      flow ++ loop(pipeStream, inputStream, evalSink)
    }


    val launch = Stream.eval(inputQueueTask).flatMap { inputQ =>
      Stream.eval(evaluationQueueTask).flatMap { evalQ =>
        Stream.eval(networkQueueTask).flatMap { networkQ =>
          {
            val inStreamEnqueue = inStream.through(inputQ.enqueue)
            val evalSink = evalQ.enqueue

            val evalToPipeGeneratorCircuit =
              evalQ.dequeue
                // .through(_.map( λ => {println(" ~~~~~ deq eval ~~~~~ "); λ} ) )
                .through(ffGA.experimentBatchPipe(layout))
                // .through(_.map( λ => {println(" ~~~~~ deq expBP ~~~~~ "); λ} ) )
                .through(networkQ.enqueue)


            val flow = loop(networkQ.dequeue, inputQ.dequeue, evalSink)
            ((flow merge inStreamEnqueue.drain) merge evalToPipeGeneratorCircuit.drain)

          }
        }
      }
    }

    launch
  }
}
