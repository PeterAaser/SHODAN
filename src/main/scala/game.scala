package SHODAN

object wallAvoid {

  val width = 10000.0
  val height = 10000.0
  val speed = 10.0
  val turnRate = 0.01
  val viewPoints = 4

  case class UnitVector(x: Double, y: Double)
  case class Coord(x: Double, y: Double)
  case class Agent(loc: Coord, heading: Double, degreesFieldOfView: Int){

    val radFieldOfView = (degreesFieldOfView.toDouble / 360.0)*2.0*PI

    // We want the agent to have 4 eyes, so we need 4 angles, one for each eye
    val viewAngles =
      (0 until viewPoints).toList
        .map( λ =>
          (λ.toDouble
          *(radFieldOfView/(viewPoints.toDouble - 1.0))
          - radFieldOfView/2.0 + heading)
      ).map(normalizeAngle)

    val distances = viewAngles.map(traceObstacleDistance(loc, _))

    def processInput(input: (Double, Double)): Double =
      normalizeAngle(heading + (input._1 - input._2)*turnRate)

    def update(input: (Double, Double)): Agent = {
      val nextX = loc.x - math.cos(heading)*speed
      val nextY = loc.y - math.sin(heading)*speed

      val normalizedNextX = if(nextX > width) width else (if (nextX < 0.0) 0 else nextX)
      val normalizedNextY = if(nextY > height) height else (if (nextY < 0.0) 0 else nextY)

      val nextHeading = processInput(input)

      copy(loc=Coord(normalizedNextX, normalizedNextY), heading=nextHeading)
    }
  }
  object Agent {

    def updateAgent(a: Agent, input: List[Double]): (Agent, List[Double]) = {

      val nextAgent = a.update((input.head, input.tail.head))
      (nextAgent, nextAgent.distances)
    }
  }

  val PI = 3.14

  def traceObstacleDistance(loc: Coord, angleRad: Double): Double = {
    val xUnitDir = if(angleRad < PI/2 || angleRad > 3*PI/2) -1 else 1
    val yUnitDir = if(angleRad < PI) -1 else 1

    val xWallDistance = math.abs(loc.x - (if(xUnitDir == 1) width else 0))
    val yWallDistance = math.abs(loc.y - (if(yUnitDir == 1) height else 0))

    val xDistance = xWallDistance/math.cos(angleRad)
    val yDistance = yWallDistance/math.sin(angleRad)

    // println(s"x: $xUnitDir")
    // println(s"y: $yUnitDir")
    // println(s"xDist: $xWallDistance")
    // println(s"yDist: $yWallDistance")
    // println(s"xDistS: $xDistance")
    // println(s"yDistS: $yDistance")
    // println(math.cos(angleRad))
    // println(math.sin(angleRad))

    if(math.abs(xDistance) > math.abs(yDistance)) yDistance else xDistance
  }

  def normalizeAngle(a: Double): Double =
    if(a > 2.0*PI) a - 2.0*PI else {if (a < 0) a + 2.0*PI else a}
}

object agentPipe {

  import fs2._

  import wallAvoid._
  import Agent._

  def wallAvoidancePipe[F[_]]: Pipe[F, List[Double], List[Double]] = {

    def go(agent: Agent): Handle[F,List[Double]] => Pull[F,List[Double],Unit] = h => {
      h.receive1 {
        case(input, h) => {
          val (nextAgent, sensorData) = updateAgent(agent, input)
          Pull.output1(input) >> go(nextAgent)(h)
        }
      }
    }

    val initAgent =
      Agent(Coord(( wallAvoid.width/2.0), (wallAvoid.height/2.0)), 0.0, 90)

    _.pull(go(initAgent))

  }

}
