package com.cyborg

import com.cyborg.rpc.AgentService
import scala.language.higherKinds

object agentPipe {

  import fs2._

  import com.cyborg.wallAvoid._
  import Agent._

  def wallAvoidancePipe[F[_]]: Pipe[F, List[Double], List[Double]] = {

    def agentPipe: Pipe[F, List[Double], (List[Double],List[Double])] = {

      def go(agent: Agent):
          Handle[F,List[Double]] => Pull[F,(List[Double], List[Double]),Unit] = h => {

        h.receive1 {
          case(input, h) => {
            val (nextAgent, sensorData) = updateAgent(agent, input)

            // TODO: this is what functional effect tries to avoid... Could be encapsulated into
            // a task maybe?
            AgentService.agentUpdate(nextAgent)

            Pull.output1(
              (sensorData, List[Double](nextAgent.loc.x, nextAgent.loc.y, nextAgent.heading))
            ) >>
              go(nextAgent)(h)
          }
        }
      }
      val initAgent =
        Agent(Coord(( wallAvoid.width/2.0), (wallAvoid.height/2.0)), 0.0, 90)

      _.pull(go(initAgent))
    }

    // This is a fucking mess, but the gist of it is that the sensorData is sent every tick.
    // Sensordata is a list of double with length eyes
    _.through(agentPipe).through(_.map(_._1))

  }

}
