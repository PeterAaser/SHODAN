package com.cyborg

import com.cyborg.Assemblers.ffANNinput
import com.cyborg.Filters.FeedForward
import com.cyborg.rpc.AgentService
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
    Observers one of those meme-loving fucks and scores it after having had enough
    of its bullshit.
    */
  def evaluatorPipe[F[_]:Async](ticksPerEval: Int, evalFunc: Double => Double): Pipe[F,Agent,Double] = {

    // Runs an agent through 1000 ticks, recording the closest it was a wall
    def challengeEvaluator(agent: Agent): Pipe[F,Agent,Double] = {
      // hardcoded
      val ticks = 1000
      def go: Handle[F,Agent] => Pull[F,Double,Unit] = h => {
        h.awaitN(ticks, false) flatMap {
          case(chunks, h) => {
            val agentLocs = chunks.foldLeft(List[Agent]())(_++_.toList)
            val minDist = agentLocs.map(_.distanceToClosest).min
            Pull.output1(minDist)
          }
        }
      }
      _.pull(go)
    }

    // Creates five (hardcoded) initial agents, each mapped to a pipe
    val challenges: List[Agent] = createChallenges
    val challengePipes = challenges.map(challengeEvaluator(_))

    // Joins the five challenges, sums the result and returns a single element stream
    val challengePipe: Pipe[F,Agent,Double] = pipe.join(Stream.emits(challengePipes))
    val result = challengePipe andThen (pipe.fold(0.0)(_+_))

    λ => λ.through(challengePipe).through(pipe.fold(.0)(_+_)).through(_.map(evalFunc))

  }


}
