package cyborg

import org.scalajs.dom
import org.scalajs.dom.html
import frontilz._

import wallAvoid._


class AgentVisualizerControl(canvas: html.Canvas, agentQueue: scala.collection.mutable.Queue[Agent]) {

  case class Point(x: Int, y: Int){
    def +(p: Point) = Point(x + p.x, y + p.y)
    def /(d: Int) = Point(x / d, y / d)
  }

  import params._

  val renderer = canvas.getContext("2d")
    .asInstanceOf[dom.CanvasRenderingContext2D]

  canvas.width = 800
  canvas.height = 800

  def toCanvasUnits(gameDistance: Double): Double = gameDistance*(canvas.width/game.width)
  def toGameUnits(canvasDistance: Double): Double = canvasDistance*(game.width/canvas.width)

  renderer.font = "16 comic sans"
  renderer.textAlign = "center"
  renderer.textBaseline = "middle"


  val PI = 3.14

  // TODO what in the name of fuck it this running variable???
  var running = false
  scalajs.js.timers.setInterval(25) {
    if(!running){
      running = true
      if(agentQueue.size > 50){
        println("UH OH AGENT QUEUE OVERFLOWING")
        println(agentQueue.size)
      }
      if(agentQueue.size > 0){
        run(agentQueue.last)
        agentQueue.dequeueAll(_ => true)
      }
      running = false
    }
  }


  def drawWalls(): Unit = {
    renderer.save()
    renderer.fillStyle = "grey"
    renderer.fillRect(0, 0, 30, canvas.height.toDouble)
    renderer.fillRect(0, 0, canvas.width.toDouble, 30)
    renderer.fillRect(canvas.width - 30.0, 0, 30, canvas.height.toDouble)
    renderer.fillRect(0, canvas.height - 30.0, canvas.width.toDouble, 30.0)
    renderer.restore()
  }


  def drawAgent(memer: Agent): Unit = {

    val screenCoords = memer.loc.toPixelCoordinates(canvas.width - 2*30, canvas.height - 2*30)

    def drawBody(): Unit = {
      renderer.save()
      renderer.fillStyle = "green"
      renderer.rotate(memer.heading)

      renderer.beginPath()
      renderer.arc(0, 0, 25.0, (Math.PI/4.0)+(Math.PI/2.0), (Math.PI/4.0)+(Math.PI), true)
      renderer.fill()
      renderer.restore()

    }

    def drawLoS(): Unit = {
      renderer.fillStyle = "blue"
        (memer.viewAngles zip memer.distances).foreach { case (angle, gameDistance) =>
          renderer.save()
          renderer.rotate(angle)

          renderer.save()
          renderer.beginPath()
          renderer.translate(26,0)


          renderer.arc(0, 0, 5.0, 0, Math.PI*2.0, false)
          renderer.fill()
          renderer.restore()

          renderer.translate(30,0)

          if (gameDistance > game.sightRange)
            renderer.fillStyle = "rgb(0, 255, 255)"
          else {
            val badness = Math.sqrt(1.0 - (gameDistance.toDouble + 1.0)/(game.sightRange + 1.0))
            val badnessc = (badness*255.0).toInt
            val memeString = s"rgb(${badnessc}, ${255 - badnessc}, ${255 - badnessc})"
            renderer.fillStyle = memeString
          }

          renderer.fillRect(0, -4, toCanvasUnits(game.sightRange), 8)
          renderer.restore()
        }
    }

    renderer.save()

    renderer.translate(40, 40)
    renderer.translate(screenCoords.x, screenCoords.y)

    renderer.rotate(Math.PI)
    drawBody
    drawLoS

    renderer.restore()
  }

  val trailLength = 500
  val trailQueue = Array.ofDim[Coord](trailLength)
  var trailHead = 0
  def drawTrail(): Unit = {
    renderer.fillStyle = "Black"
    for(ii <- 0 until trailLength){
      var idx = (ii + trailHead) % 500
      if(trailQueue(idx) != null){
        renderer.fillRect(
          trailQueue(idx).x,
          trailQueue(idx).y,
          2,
          2
        )
      }
    }
  }


  def draw(agent: Agent): Unit = {

    renderer.save();
    drawTrail()
    drawAgent(agent)
    drawWalls
    renderer.fillStyle = "black"
    val memeString1 = s"x: ${agent.loc.x}"
    val memeString2 = s"y: ${agent.loc.y}"
    renderer.fillText(memeString1, 200, 200)
    renderer.fillText(memeString2, 200, 230)
    renderer.restore();
  }



  def run(agent: Agent): Unit = {

    // Renders horribly wrong lol.
    trailQueue(trailHead) = agent.loc.toPixelCoordinates(canvas.width, canvas.height)
    trailHead = (trailHead + 1) % 500

    say("rendering agent called")
    renderer.clearRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)
    renderer.fillStyle = "rgb(212, 212, 212)"
    renderer.fillRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)

    draw(agent)
  }


  def update(a: Agent): Unit = {
    run(a)
  }
}
