package SHODAN

import scala.language.higherKinds

object agentPipe {

  import fs2._

  import wallAvoid._
  import Agent._

  def wallAvoidancePipe[F[_]]: Pipe[F, List[Double], List[Double]] = {

    def go(agent: Agent): Handle[F,List[Double]] => Pull[F,List[Double],Unit] = h => {
      h.receive1 {
        case(input, h) => {
          val (nextAgent, sensorData) = updateAgent(agent, input)
          // println(s"wow game thing, the agent saw $sensorData")
          Pull.output1(sensorData) >> go(nextAgent)(h)
        }
      }
    }

    val initAgent =
      Agent(Coord(( wallAvoid.width/2.0), (wallAvoid.height/2.0)), 0.0, 90)

    _.pull(go(initAgent))

  }

}
