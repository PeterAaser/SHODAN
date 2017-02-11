package com.cyborg

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

object Visualizer {
  class VisualizerControl(canvas: html.Canvas) {

    val renderer = canvas.getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]

    canvas.width = 800
    canvas.height = 800

    renderer.font = "16 comic sans"
    renderer.textAlign = "center"
    renderer.textBaseline = "middle"

    val PI = 3.14

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
      renderer.clearRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)
      renderer.fillStyle = "rgb(212, 212, 212)"
      renderer.fillRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)

      draw(agent)
    }

    def update(a: Agent): Unit = {
      run(a)
    }
  }
}
