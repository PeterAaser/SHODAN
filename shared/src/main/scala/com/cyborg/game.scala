package com.cyborg

object wallAvoid {

  val width = 10000.0
  val height = 10000.0
  val speed = 10.0
  val turnRate = 0.01
  val viewPoints = 4

  case class UnitVector(x: Double, y: Double)
  case class Coord(x: Double, y: Double){
    def toPixelCoordinates(screenWidth: Int, screenHeight: Int): Coord =
      copy(x = (x/width.toDouble)*screenWidth.toDouble,
           y = (y/height.toDouble)*screenHeight.toDouble)
  }
  case class Agent(loc: Coord, heading: Double, degreesFieldOfView: Int){

    val radFieldOfView = (degreesFieldOfView.toDouble / 360.0)*2.0*PI

    // We want the agent to have 4 eyes, so we need 4 angles, one for each eye
    val viewAngles: List[Double] =
      (0 until viewPoints).toList
        .map( λ =>
          (λ.toDouble
          *(radFieldOfView/(viewPoints.toDouble - 1.0))
          - radFieldOfView/2.0 + heading)
      ).map(normalizeAngle)

    val distances = viewAngles.map{traceObstacleDistance(loc, _)}

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

    def updateAgent(a: Agent, input: List[Double]): Agent = {

      val nextAgent = a.update((input.head, input.tail.head))
      nextAgent
    }
  }

  val PI = 3.14

  def traceObstacleDistance(loc: Coord, angleRad: Double): Double = {
    val xUnitDir = if(angleRad < PI/2 || angleRad > 3*PI/2) -1 else 1
    val yUnitDir = if(angleRad < PI) -1 else 1

    val xWallDistance = math.abs(loc.x - (if(xUnitDir == 1) width else 0))
    val yWallDistance = math.abs(loc.y - (if(yUnitDir == 1) height else 0))

    val xDistance = xWallDistance/math.abs(math.cos(angleRad))
    val yDistance = yWallDistance/math.abs(math.sin(angleRad))

    // println(s"angle: $angleRad")
    // println(s"x unit direction: $xUnitDir")
    // println(s"y unit direction: $yUnitDir")
    // println(s"x normal dist: $xWallDistance")
    // println(s"y normal dist: $yWallDistance")
    // println(s"x perceived distance: $xDistance")
    // println(s"y perceived distance: $yDistance")
    // println(s"some angle ${math.cos(angleRad)}")
    // println(s"some other angle ${math.sin(angleRad)}")
    // println("\n\n")
    // println("----")

    if(math.abs(xDistance) > math.abs(yDistance)) yDistance else xDistance
  }

  def normalizeAngle(a: Double): Double =
    if(a > 2.0*PI) a - 2.0*PI else {if (a < 0) a + 2.0*PI else a}
}
