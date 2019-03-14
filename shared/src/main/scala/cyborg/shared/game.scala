package cyborg

// import utilz._

object wallAvoid {

  import com.avsystem.commons.serialization.GenCodec
  import params.game._


  case class UnitVector(x: Double, y: Double)
  case class Coord(x: Double, y: Double){
    def toPixelCoordinates(screenWidth: Int, screenHeight: Int): Coord =
      copy(x = (x/width.toDouble)*screenWidth.toDouble,
           y = (y/height.toDouble)*screenHeight.toDouble)
  }
  object Coord {
    implicit val coordCodec: GenCodec[Coord] = GenCodec.materialize
  }
  case class Agent(loc: Coord, heading: Double, degreesFieldOfView: Int){

    val radFieldOfView = (degreesFieldOfView.toDouble / 360.0)*2.0*PI

    val viewAngles: List[Double] =
      (0 until viewPoints).toList
        .map( x =>
          (x.toDouble
          *(radFieldOfView/(viewPoints.toDouble - 1.0))
          - radFieldOfView/2.0 + heading)
      ).map(normalizeAngle)

    val distances = viewAngles.map{traceObstacleDistance(loc, _)}

    val distanceToClosest: Double = {
      val xDistLeft = loc.x
      val xDistRight = width - loc.x
      val yDistTop = loc.y
      val yDistBot = height - loc.y
      List(xDistLeft, xDistRight, yDistBot, yDistTop).min
    }

    // def processInput(input: (Double, Double)): Double =
    def updateBearing(input: (Double, Double)): Double = {

      val angleDeltaRaw = (input._1 - input._2)*turnRate
      val angleDelta =
        if (angleDeltaRaw > maxTurnRate)
          maxTurnRate
        else if (angleDeltaRaw < -maxTurnRate)
          -maxTurnRate
        else
          angleDeltaRaw

      normalizeAngle(heading + angleDelta)
    }

    def update(input: (Double, Double)): Agent = {

      val nextX = loc.x - math.cos(heading)*speed
      val nextY = loc.y - math.sin(heading)*speed

      val normalizedNextX = if(nextX > width) width else (if (nextX < 0.0) 0 else nextX)
      val normalizedNextY = if(nextY > height) height else (if (nextY < 0.0) 0 else nextY)

      val nextHeading = updateBearing(input)

      copy(loc=Coord(normalizedNextX, normalizedNextY), heading=nextHeading)
    }

    def updateNoTurn(input: (Double, Double)): Agent = {

      val nextX = loc.x - math.cos(heading)*speed
      val nextY = loc.y - math.sin(heading)*speed

      val normalizedNextX = if(nextX > width) width else (if (nextX < 0.0) 0 else nextX)
      val normalizedNextY = if(nextY > height) height else (if (nextY < 0.0) 0 else nextY)

      copy(loc=Coord(normalizedNextX, normalizedNextY))
    }


    def autopilot: Agent =
      if(distances.min > params.game.sightRange)
        update(0.0, 0.0)
      else if(distances.head > distances.last)
        update(0.0, 1.0)
      else if(distances.head < distances.last)
        update(1.0, 0.0)
      else
        update(0.0, 0.0)

    def neutral: Agent = update(0.0, 0.0)
      // if(distances.min > params.game.sightRange)
      //   update(0.0, 0.0)
      // else if(distances.head > distances.last)
      //   update(0.0, 1.0)
      // else if(distances.head < distances.last)
      //   update(1.0, 0.0)
      // else
      //   update(0.0, 0.0)


    override def toString: String = {
      val locS = "[%.2f][%.2f]".format(loc.x, loc.y)
      val headS = "%.3f".format(heading)
      val viewS = distances.map(_.toInt).mkString(", ")
      s"Agent - at $locS, bearing $headS seeing $viewS"
    }
  }
  object Agent {

    // val init = Agent(Coord(8000.0, 5000.0), 3.14, 120)
    def init = {import params.game._; Agent(Coord(( width/2.0), ( height/2.0)), 0.0, 90) }

    def updateAgent(a: Agent, input: List[Double]): Agent = {
      val nextAgent = a.update((input.head, input.tail.head))
      nextAgent
    }
    def updateAgentNoTurn(a: Agent, input: List[Double]): Agent = {
      val nextAgent = a.updateNoTurn((input.head, input.tail.head))
      nextAgent
    }
    implicit val agentCodec: GenCodec[Agent] = GenCodec.materialize

  }

  val PI = 3.14

  def createChallenges: List[Agent] = {

    val straightRun = Agent(Coord(2000.0, 5000.0), PI, 80)

    val loc = Coord(6000.0, 5000.0)
    val firstAngle  = PI + PI/6.0
    val secondAngle = PI + PI/12.0
    val thirdAngle  = PI + .0
    val fourthAngle = PI + -PI/6.0
    val fifthAngle  = PI + -PI/12.0

    val agentPA = Agent(loc, _: Double, 80)

    List(
      // agentPA(firstAngle),
      agentPA(secondAngle),
      straightRun,
      agentPA(fourthAngle),
      // agentPA(fifthAngle)
    )
  }

  def traceObstacleDistance(loc: Coord, angleRad: Double): Double = {
    val xUnitDir = if(angleRad < PI/2 || angleRad > 3*PI/2) -1 else 1
    val yUnitDir = if(angleRad < PI) -1 else 1

    val xWallDistance = math.abs(loc.x - (if(xUnitDir == 1) width  else 0))
    val yWallDistance = math.abs(loc.y - (if(yUnitDir == 1) height else 0))

    val xDistance = xWallDistance/math.abs(math.cos(angleRad))
    val yDistance = yWallDistance/math.abs(math.sin(angleRad))

    Math.max(
      Math.min(params.game.sightRange, if(math.abs(xDistance) > math.abs(yDistance)) yDistance else xDistance),
      params.game.deadZone
    )

  }

  def normalizeAngle(a: Double): Double =
    if(a > 2.0*PI) a - 2.0*PI else {if (a < 0) a + 2.0*PI else a}
}
