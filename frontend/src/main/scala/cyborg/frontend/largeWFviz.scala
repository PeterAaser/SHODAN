package cyborg

import org.scalajs.dom
import org.scalajs.dom.html
import frontilz._

import cyborg.frontend.services.rpc.RPCService
import cyborg.RPCmessages.DrawCommand

class LargeWFviz(canvas: html.Canvas) {

  canvas.width = 1000
  canvas.height = 400

  val renderer = canvas.getContext("2d")
    .asInstanceOf[dom.CanvasRenderingContext2D]

  val frameQueue = new scala.collection.mutable.Queue[Array[Array[DrawCommand]]]()

  /**
    * The exposed method to push data
    * Based on the one from wfViz
    * 
    * A batch is pushed every second, thus the size of the batch tells the canvas
    * how many pixels must be pushed per update
    * 
    * Targetting 40 FPS we need to do draw calls every 25ms
    * 
    * Each frame gets pushed into the queue, which is then read from at 25ms intervals,
    * unless empty.
    * This means that framesize alone (and by extension, batch size) controls the "speed"
    * of data.
    */
  def pushData(data: Array[Array[DrawCommand]]): Unit = {

    val points = data.size
    val framerateTarget = 40

    // cba dealing with fractions. So what if we drop a few drawcalls?
    val pointsPerFrame = points/framerateTarget
    val frames: Array[Array[Array[DrawCommand]]] = data.grouped(pointsPerFrame).toArray
    frames.foreach(frame => frameQueue.enqueue(frame))
  }


  scalajs.js.timers.setInterval(26) {
    if(frameQueue.size > 60){
      // double speed if more than 60 frames are buffered
      gogo(frameQueue.dequeue)
    }
    if(frameQueue.size > 0){
      gogo(frameQueue.dequeue)
    }
  }


  def fillRectAbs(xLeft: Int, xRight: Int, yTop: Int, yBot: Int): Unit = {
    val height = yTop - yBot
    val width  = xRight - xLeft
    renderer.fillRect(xLeft, yTop, width, height)
  }

  var drawCommands = Array.ofDim[Array[DrawCommand]](1000)
  def addToCommandArray(commands: Array[Array[DrawCommand]]): Unit = {
    val (remainder, _) = drawCommands.splitAt(1000 - commands.size)
    drawCommands = commands.reverse ++ remainder
  }

  def drawCommand(cmd: DrawCommand, x: Int): Unit = {
    renderer.fillStyle =
      if(cmd.color == 0) "orange"
      else if(cmd.color == 1) "green"
      else if(cmd.color == 2) "cyan"
      else "black"

    val hi = cmd.yMax/15
    val lo = cmd.yMin/15

    val(y_offset, height) = if((hi - lo) > 5){
      (200 - hi,
        hi - lo)
    }
    else{
      (200 - (hi+5),
        (hi+5) - (lo-5))
    }

    renderer.fillRect(x, y_offset, 2, height)
  }

  def drawCommandArray(cmds: Array[DrawCommand], x: Int): Unit = cmds.foreach(drawCommand(_, x))

  def drawPixelArray(): Unit = {
    for(ii <- 0 until 1000){
      if(drawCommands(ii) != null){
        drawCommandArray(drawCommands(ii), ii)
      }
    }
  }

  def clear(): Unit = {
    renderer.fillStyle = "rgb(211, 211, 211)"
    renderer.fillRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)
    renderer.fillStyle = "grey"
    renderer.fillRect(1000, 0, 1000, 400)

  }

  def lines(): Unit = {
    renderer.fillStyle = "darkSlateGray"
    fillRectAbs(0, 1000, 99, 101)
    fillRectAbs(0, 1000, 199, 201)
    fillRectAbs(0, 1000, 299, 301)
  }

  def gogo(data: Array[Array[DrawCommand]]): Unit = {
    addToCommandArray(data)
    clear()
    drawPixelArray()
    lines()
  }

}
