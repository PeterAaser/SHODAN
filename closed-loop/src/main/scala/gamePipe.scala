package SHODAN

import scala.language.higherKinds

object agentPipe {

  import fs2._

  import wallAvoid._
  import Agent._

  def wallAvoidancePipe[F[_]](observerPipe: Pipe[F,(List[Double], List[Double]), (List[Double],List[Double])]):
      Pipe[F, List[Double], List[Double]] = {

    def agentPipe: Pipe[F, List[Double], (List[Double],List[Double])] = {

      def go(agent: Agent):
          Handle[F,List[Double]] => Pull[F,(List[Double], List[Double]),Unit] = h => {

        h.receive1 {
          case(input, h) => {
            val (nextAgent, sensorData) = updateAgent(agent, input)

            Pull.output1( (sensorData, List[Double](nextAgent.loc.x, nextAgent.loc.y)) ) >> go(nextAgent)(h)
          }
        }
      }
      val initAgent =
        Agent(Coord(( wallAvoid.width/2.0), (wallAvoid.height/2.0)), 0.0, 90)

      _.pull(go(initAgent))
    }

    _.through(agentPipe).through(observerPipe).through(_.map(_._1))

  }

}
