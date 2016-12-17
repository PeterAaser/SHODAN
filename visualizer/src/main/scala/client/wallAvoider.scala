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

    // var wallAvoider = wallAvoid.Agent(
    //   wallAvoid.Coord(8000.0, 4000.0),
    //   PI/2.0,
    //   120)

    def drawWalls: Unit = {
      renderer.save()
      renderer.fillStyle = "grey"
      renderer.fillRect(0, 0, 30, canvas.height)
      renderer.fillRect(0, 0, canvas.width, 30)
      renderer.fillRect(canvas.width - 30, 0, 30, canvas.height)
      renderer.fillRect(0, canvas.height - 30, canvas.width, 30)
      renderer.restore()
    }


    def drawAgent(memer: Agent): Unit = {

      val screenCoords = memer.loc.toPixelCoordinates(canvas.width - 2*30, canvas.height - 2*30)


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
        renderer.fillStyle = "blue"
        (memer.viewAngles zip memer.distances).foreach { case (angle, distance) =>
          renderer.save()
          renderer.rotate(angle)

          renderer.save()
          renderer.beginPath()
          renderer.translate(26,0)


          renderer.arc(0, 0, 5.0, 0, Math.PI*2.0, false)
          renderer.fill()
          renderer.restore()

          renderer.translate(30,0)

          if (distance > 3000)
            renderer.fillStyle = "rgb(0, 255, 255)"
          else {
            val badness = Math.sqrt(1.0 - (distance.toDouble + 1.0)/(3000.0 + 1.0))
            val badnessc = (badness*255.0).toInt
            val memeString = s"rgb(${badnessc}, ${255 - badnessc}, ${255 - badnessc})"
            renderer.fillStyle = memeString
          }

          renderer.fillRect(0, -4, 180, 8)
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

    def draw(agent: Agent): Unit = {

      renderer.save();
      drawAgent(agent)
      drawWalls
      renderer.fillStyle = "black"
      val memeString1 = s"x: ${agent.loc.x}"
      val memeString2 = s"y: ${agent.loc.y}"
      val eyeString1 = s"eye 1: ${agent.distances(0)}"
      val eyeString2 = s"eye 2: ${agent.distances(1)}"
      val eyeString3 = s"eye 3: ${agent.distances(2)}"
      val eyeString4 = s"eye 4: ${agent.distances(3)}"
      renderer.fillText(memeString1, 200, 200)
      renderer.fillText(memeString2, 200, 230)
      renderer.fillText(eyeString1, 200, 260)
      renderer.fillText(eyeString2, 200, 290)
      renderer.fillText(eyeString3, 200, 320)
      renderer.fillText(eyeString4, 200, 350)
      renderer.restore();
    }

    def run(agent: Agent): Unit = {
      renderer.clearRect(0, 0, canvas.width, canvas.height)
      renderer.fillStyle = "rgb(212, 212, 212)"
      renderer.fillRect(0, 0, canvas.width, canvas.height)

      draw(agent)
    }

    noIdea
    // js.timers.setInterval(20) {
    //   run
    // }

    def dieFugger(a: dom.MessageEvent): Unit = {

      val ayy = a.data.toString.split(",")
        .map(_.replace('[', ' '))
        .map(_.replace(']', ' '))
        .map(_.trim)
        .map(_.toDouble)


      val myAgent = Agent(
        Coord(ayy(0) + 10000.0, ayy(1) + 10000.0),
        ayy(2),
        120
      )

      println(myAgent)
      run(myAgent)

    }

    def receive(e: dom.MessageEvent): Unit = {
      val data = js.JSON.parse(e.data.toString)

      // haha fug x---D
      // wallAvoider = wallAvoider.copy(
      //   loc=wallAvoider.loc.copy(
      //     x = data.x.toString.toDouble,
      //     y = data.y.toString.toDouble
      //   ),
      //   heading=data.heading.toString.toDouble)

      // renderer.clearRect(0, 0, canvas.width, canvas.height)
      // renderer.fillStyle = "rgb(212, 212, 212)"
      // renderer.fillRect(0, 0, canvas.width, canvas.height)
      // val (nextAgent, output) = Agent.updateAgent(wallAvoider, List(-0.1, 0.2))
      // wallAvoider = nextAgent
      // draw(nextAgent)
    }

    def noIdea: Unit = {
      val someUri = "ws://127.0.0.1:9897"
      val socket = new dom.WebSocket(someUri)
      socket.onmessage = dieFugger _

      js.timers.setInterval(20) {
        socket.send("hello")
      }
    }
  }
}
