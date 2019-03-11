package cyborg

import org.scalajs.dom
import org.scalajs.dom.html
import frontilz._
import bonus._

import cyborg.frontend.services.rpc.RPCService
import cyborg.RPCmessages.DrawCommand

class SpikeTrainViz(canvas: html.Canvas) {

  val width = 2000
  canvas.width = width
  canvas.height = 400

  val nRows = 10

  val cellWidth = width/60
  val cellHeight = canvas.height/nRows

  val renderer = canvas.getContext("2d")
    .asInstanceOf[dom.CanvasRenderingContext2D]


  def drawGrid(): Unit = {
    renderer.fillStyle = "black"
    for(row <- 0 until 10){
      renderer.fillRect(0, row*cellHeight, width, 4)         // |
    }
    for(col <- 0 until 60){
      renderer.fillRect(col*cellWidth, 0, 4, canvas.height)  // -
    }
  }


  def pushData(data: Array[DrawCommand]): Unit = {

    val frame = data.grouped(60).toArray
    def drawRows(): Unit = {
      for(rowNo <- 0 until nRows){
        drawRow(frame(rowNo), rowNo)
      }
    }
    drawGrid()
    drawRows()
    // say(s"Drew frame of size ${data.size}")
    // say(s"row zero: ${frame(0).map(_.yMin).toList}")
    // say(s"row one: ${frame(1).map(_.yMin).toList}")
  }


  def drawRow(cmds: Array[DrawCommand], y: Int): Unit = {
    for(colNo <- 0 until 60){
      val dc = cmds(colNo).yMin
      getColor(dc)
      renderer.fillRect(2 + (colNo*cellWidth), 2 + (y*cellHeight), cellWidth, cellHeight)
    }
  }




  def getColor(spikes: Int): Unit = {
    val maxSpikes = 40
    val maxBlue = 127

    val badness = if(spikes > 40) 1.0 else Math.sqrt(spikes.toDouble/maxSpikes.toDouble)
    val badnessR = 255.0*(1.0 - badness)
    val badnessB = 126.0 + 127.0*(1.0 - badness)
    val colorString = s"rgb(${badnessR.toInt},255,${badnessB.toInt})"

    // say(s"for $spikes, the color was $colorString")

    renderer.fillStyle = colorString
  }
}
