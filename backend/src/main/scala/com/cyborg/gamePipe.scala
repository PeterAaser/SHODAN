package com.cyborg

import com.cyborg.Assemblers.ffANNinput
import com.cyborg.rpc.AgentService
import scala.language.higherKinds

object agentPipe {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  import fs2._

  import com.cyborg.wallAvoid._
  import Agent._

  def wallAvoidancePipe[F[_]]: Pipe[F, ffANNoutput, Agent] = {

    val initAgent =
      Agent(Coord(( wallAvoid.width/2.0), (wallAvoid.height/2.0)), 0.0, 90)

    def go(agent: Agent): Handle[F, ffANNoutput] => Pull[F, Agent, Unit] = h => {
      h.receive1 {
        case (input, h) => {
          val nextAgent = updateAgent(agent, input)
          Pull.output1(agent) >> go(nextAgent)(h)
        }
      }
    }

    _.pull(go(initAgent))

  }

  //         AgentService.agentUpdate(nextAgent)
}
