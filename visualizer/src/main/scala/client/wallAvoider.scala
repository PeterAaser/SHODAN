package SHODAN

import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom
import org.scalajs.dom.html
import scala.util.Random
import scalajs.js

import wallAvoid._

case class Point(x: Int, y: Int){
  def +(p: Point) = Point(x + p.x, y + p.y)
  def /(d: Int) = Point(x / d, y / d)
}

@JSExport
object ScalaJSExample {
  @JSExport
  def main(canvas: html.Canvas): Unit = {

    val renderer = canvas.getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]


    canvas.width = 800
    canvas.height = 800

    renderer.font = "16 comic sans"
    renderer.textAlign = "center"
    renderer.textBaseline = "middle"

    val PI = 3.14

    var wallAvoider = wallAvoid.Agent(
      wallAvoid.Coord(5000.0, 5000.0),
      PI/2.0,
      120)

    def drawWalls: Unit = {
      renderer.save()
      renderer.fillStyle = "grey"
      renderer.fillRect(10, 10, 30, canvas.height)
      renderer.fillRect(10, 10, canvas.width, 30)
      renderer.fillRect(canvas.width - 30, 10, 30, canvas.height)
      renderer.fillRect(10, canvas.height - 30, canvas.width, 30)
      renderer.restore()
    }


    def drawAgent(memer: Agent): Unit = {

      val screenCoords = memer.loc.toPixelCoordinates(canvas.width, canvas.height)

      def drawBody: Unit = {
        renderer.save()
        renderer.fillStyle = "green"
        renderer.rotate(memer.heading)

        renderer.beginPath()
        renderer.arc(0, 0, 25.0, (Math.PI/4.0)+(Math.PI/2.0), (Math.PI/4.0)+(Math.PI), true)
        renderer.fill()
        renderer.restore()

      }

      def drawLoS: Unit = {
        renderer.fillStyle = "cyan"
        (memer.viewAngles zip memer.distances).foreach { case (angle, distance) =>
          renderer.save()
          renderer.rotate(angle)

          renderer.save()
          renderer.beginPath()
          renderer.translate(26,0)
          if (distance > 3000)
            renderer.fillStyle = "blue"
          else
            renderer.fillStyle = "red"

          renderer.arc(0, 0, 5.0, 0, Math.PI*2.0, false)
          renderer.fill()
          renderer.restore()

          renderer.translate(30,0)
          renderer.fillRect(0, -4, 180, 8)
          renderer.restore()
        }
      }

      renderer.save()

      renderer.translate(screenCoords.x, screenCoords.y)

      renderer.rotate(Math.PI)
      drawBody
      drawLoS

      renderer.restore()
    }

    def draw(agent: Agent): Unit = {

      renderer.save();
      drawAgent(agent)
      drawWalls
      val memeString1 = s"x: ${wallAvoider.loc.x}"
      val memeString2 = s"y: ${wallAvoider.loc.y}"
      val eyeString1 = s"eye 1: ${wallAvoider.distances(0)}"
      val eyeString2 = s"eye 2: ${wallAvoider.distances(1)}"
      val eyeString3 = s"eye 3: ${wallAvoider.distances(2)}"
      val eyeString4 = s"eye 4: ${wallAvoider.distances(3)}"
      renderer.fillText(memeString1, 200, 200)
      renderer.fillText(memeString2, 200, 230)
      renderer.fillText(eyeString1, 200, 260)
      renderer.fillText(eyeString2, 200, 290)
      renderer.fillText(eyeString3, 200, 320)
      renderer.fillText(eyeString4, 200, 350)
      renderer.restore();
    }

    drawAgent(wallAvoider)

    def run: Unit = {
      renderer.clearRect(0, 0, canvas.width, canvas.height)
      val (nextAgent, output) = Agent.updateAgent(wallAvoider, List(0.3, -0.3))
      wallAvoider = nextAgent
      draw(nextAgent)
    }

    js.timers.setInterval(50) {
      run
    }

    {
      /*game logic*/

      // def runLive() = {
      //   frame += 2

      //   if (frame >= 0 && frame % obstacleGap == 0)
      //     obstacles.enqueue(Random.nextInt(canvas.height - 2 * holeSize) + holeSize)

      //   if (obstacles.length > 7){
      //     obstacles.dequeue()
      //     frame -= obstacleGap
      //   }


      //   playerY = playerY + playerV
      //   playerV = playerV + gravity

      //   renderer.fillStyle = "darkblue"
      //   for((holeY, i) <- obstacles.zipWithIndex){
      //     val holeX = i * obstacleGap - frame + canvas.width
      //     renderer.fillRect(holeX, 0, 5, holeY - holeSize)
      //     renderer.fillRect(holeX, holeY + holeSize, 5, canvas.height - holeY - holeSize)

      //     if (math.abs(holeX - canvas.width/2) > 5 &&
      //           math.abs(holeY - playerY) > holeSize){

      //       dead = 50
      //     }
      //   }

      //   renderer.fillStyle = "darkgreen"
      //   renderer.fillRect(canvas.width /2 - 5, playerY - 5, 10, 10)

      //   if (playerY < 0 || playerY > canvas.height){
      //     dead = 50
      //   }
      // }

      // def runDead() = {
      //   playerY = canvas.height / 2
      //   playerV = 0
      //   frame = -50
      //   obstacles.clear()
      //   dead -= 1
      //   renderer.fillStyle = "darkred"
      //   renderer.fillText("Game Over", canvas.width / 2, canvas.height / 2)
      // }

      // def run() = {
      //   renderer.clearRect(0,0, canvas.width, canvas.height)
      //   if( dead > 0) runDead()
      //   else runLive()
      // }

      // dom.setInterval(run _, 20)

      // canvas.onclick = (e: dom.MouseEvent) => {
      //   playerV -= 5
      // }
    }
  }
}
