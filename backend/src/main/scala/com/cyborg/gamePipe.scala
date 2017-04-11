package com.cyborg

import com.cyborg.Assemblers.ffANNinput
import com.cyborg.Filters.FeedForward
import com.cyborg.rpc.AgentService
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


  def evaluatorPipe[F[_]](
    ticks: Int,
    evaluatorFunction: Agent => Double,
    netGenerator: FeedForward[Double] => FeedForward[Double]

  ): Pipe[F, ffANNinput, Double] = {


    // def evalPipe(
    //   ticksLeft: Int,
    //   score: Double,
    //   filter: Pipe[F, ffANNinput, Agent]
    // ): Handle[F, ffANNinput] => Pull[F, Agent, Unit] =
    //   h => {
    //     h.through(filter).receive1 {
    //       case (agent, h) => {
    //         if(ticksLeft == 0)
    //           ???
    //         else
    //           Pull.output1(agent) >> evalPipe(ticksLeft - 1, score + evaluatorFunction(agent))(h)
    //       }
    //     }

    //   ???
    // }

    ???
  }
}
