package com.cyborg

import com.cyborg.Assemblers.ffANNinput
import com.cyborg.Filters.FeedForward
import fs2.util.Async
import scala.language.higherKinds

object agentPipe {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  import fs2._

  import com.cyborg.wallAvoid._
  import Agent._

  val initAgent =
    Agent(Coord(( wallAvoid.width/2.0), (wallAvoid.height/2.0)), 0.0, 90)


  def wallAvoidancePipe[F[_]](init: Agent = initAgent): Pipe[F, ffANNoutput, Agent] = {

    def go(agent: Agent): Handle[F, ffANNoutput] => Pull[F, Agent, Unit] = h => {
      h.receive1 {
        case (input, h) => {
          val nextAgent = updateAgent(agent, input)
          Pull.output1(agent) >> go(nextAgent)(h)
        }
      }
    }

    _.pull(go(init))

  }


  /**
    Sets up 5 challenges, evaluates ANN performance and returns
    the evaluation via the eval sink
    */
  def evaluatorPipe[F[_]:Async](
    ticksPerEval: Int,
    evalFunc: Double => Double,
    evalSink: Sink[F,Double]): Pipe[F,ffANNoutput,Agent] = {


    // println("running evaluatorPipe")

    // Runs an agent through ticksPerEval ticks, recording the closest it was a wall
    // and halts
    def challengeEvaluator(agent: Agent): Pipe[F,ffANNoutput,Agent] = {

      def go(ticks: Int, agent: Agent): Handle[F,ffANNoutput] => Pull[F,Agent,Unit] = h => {
        h.receive1 {
          (agentInput, h) => {
            val nextAgent = updateAgent(agent, agentInput)
            if (ticks > 0)
              Pull.output1(nextAgent) >> go(ticks - 1, nextAgent)(h)
            else {
              // println(" →→→→→→→→→→→→→→→→→→ Evaluation complete ←←←←←←←←←←←←←←←←←←←← ")
              Pull.output1(nextAgent)
              }
          }
        }
      }
      _.pull(go(ticksPerEval, agent))
    }

    def evaluateRun: Pipe[F,Agent,Double] = {
      def go: Handle[F,Agent] => Pull[F,Double,Unit] = h => {
        h.awaitN(ticksPerEval, false) flatMap {
          case (chunks, _) => {
            // println("evalRun evaluating")
            val closest = chunks.map(_.toList).flatten
              .map(_.distanceToClosest)
              .min
            Pull.output1(closest)
          }
        }
      }
      _.pull(go)
    }


    // attaches an evaluator to a joined pipe of n experiments
    def attachSink(
      experimentPipe: Pipe[F,ffANNoutput,Agent],
      evalSink: Sink[F,Double]): Pipe[F,ffANNoutput,Agent] = s => {

      // println("Attaching single sink!")

      val t = s.through(experimentPipe)
      pipe.observe(t)(λ =>
        λ.through(evaluateRun)
          .through(pipe.fold(.0)(_+_)).through(_.map(evalFunc(_)))
          // .through(_.map(λ => {println(s" enqueuing the evaluation $λ"); λ}))
          .through(evalSink)
      )
    }

    // Creates five (hardcoded) initial agents, each mapped to a pipe
    val challenges: List[Agent] = createChallenges
    val challengePipes: List[Pipe[F,ffANNoutput,Agent]] = challenges.map(challengeEvaluator(_))

    // Joins the five challenges, attaches an evaluator to the joined pipe
    val challengePipe: Pipe[F,ffANNoutput,Agent]
      = pipe.join(Stream.emits(challengePipes.map(attachSink(_, evalSink))))

    s: Stream[F,ffANNoutput] => s.through(challengePipe).through(pipe.take(ticksPerEval.toLong*5))
  }

  def testEvaluatorPipe[F[_]:Async](
    ticksPerEval: Int,
    evalFunc: Double => Double
    ): Pipe[F,ffANNoutput,Agent] = {


    println("running evaluatorPipe")

    // Runs an agent through 1000 ticks, recording the closest it was a wall
    def challengeEvaluator(agent: Agent): Pipe[F,ffANNoutput,Agent] = {

      def go(ticks: Int, agent: Agent): Handle[F,ffANNoutput] => Pull[F,Agent,Unit] = h => {
        h.receive1 {
          (agentInput, h) => {
            val nextAgent = updateAgent(agent, agentInput)
            if (ticks > 0)
              Pull.output1(nextAgent) >> go(ticks - 1, nextAgent)(h)
            else
              Pull.output1(nextAgent)
          }
        }
      }
      _.pull(go(ticksPerEval, agent))
    }

    def evaluateRun: Pipe[F,Agent,Double] = {
      def go: Handle[F,Agent] => Pull[F,Double,Unit] = h => {
        println("test evaluate run called")
        h.awaitN(ticksPerEval, false) flatMap {
          case (chunks, _) => {
            println("test evaluate flatmapping")
            val closest = chunks.map(_.toList).flatten
              .map(_.distanceToClosest)
              .min
            Pull.output1(closest)
          }
        }
      }
      _.pull(go)
    }

    // Creates five (hardcoded) initial agents, each mapped to a pipe
    val challenges: List[Agent] = createChallenges
    val challengePipes = challenges.map(challengeEvaluator(_))

    // Joins the five challenges, attaches an evaluator to the joined pipe
    val challengePipe: Pipe[F,ffANNoutput,Agent] =
      pipe.join(Stream.emits(challengePipes))

    challengePipe
  }
}
