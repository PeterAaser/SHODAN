package cyborg

import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import frontilz._

import cyborg.frontend.services.rpc.RPCService
import cyborg.RPCmessages.DrawCommand

class WFVisualizerControl(
  canvas                : Canvas,
  dataqueue             : scala.collection.mutable.Queue[Array[DrawCommand]],
  channelClickedHandler : Int => Unit
) {

  import params.waveformVisualizer._
  canvas.width = vizLength*8 + 4
  canvas.height = vizHeight*8 + 4

  var clickyX = 0
  var clickyY = 0
  var vizUserIdx = 0
  var vizChannelIdx = 0

  val renderer = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  // sadly doesn't work, no comic sans 4 u :(
  renderer.font = "16 comic sans"

  renderer.textAlign = "center"
  renderer.textBaseline = "middle"
  renderer.fillStyle = "yellow"

  val pixels = Array.ofDim[DrawCommand](60,vizLength)
  for(ii <- 0 until pixels.size)
    for(kk <- 0 until pixels(ii).size)
      pixels(ii)(kk) = DrawCommand(0,0,0)

  /**
    Some tuple rearranging to get visualizing to appear as it does in MCS explorer, 
    but conceptually simple if you just follow the types.
    */
  val topRowWithCoords: List[(Int, Int)] =
    (1 to 6).map(x => (x,0)).toList

  val botRowWithCoords: List[(Int, Int)] =
    (1 to 6).map(x => (x,7)).toList

  val middleRowsWithCoords = (0 until 48).sliding(8,8)
    .map( row => row zip (0 to 7) ).toList.transpose
    .map( column => column zip (1 to 6) )
    .map(_.map(x => (x._1._2, x._2)))
    .transpose
    .flatten

  val channelsWithCoordinates: List[(Int,Int)] =
    topRowWithCoords ::: middleRowsWithCoords ::: botRowWithCoords


  // var groupSize = 10
  // TODO what in the name of fuck it this running variable???
  var running = false
  scalajs.js.timers.setInterval(50) {
    if(!running){
      running = true
      if(dataqueue.size > 500){
        println(dataqueue.size)
      }
      if(dataqueue.size > 0){
        val hurr = dataqueue.dequeue()
        gogo(hurr)
      }
      running = false
    }
  }

  /**
    Chop incoming data into chunks for each channel
    */
  def distributeDrawCall(data: Array[DrawCommand]): Unit = {
    val groupSize = data.size/60
    val chopped = data.grouped(groupSize).toList
    for(index <- 0 until 60){
      val (remainder, _) = pixels(index) splitAt(vizLength - groupSize)
      pixels(index) = (chopped(index).reverse ++ remainder)
    }
  }



  def gogo(data: Array[DrawCommand]): Unit = {
    distributeDrawCall(data)

    clear()
    drawPixelArrays()
    drawGrid()
    drawChannelInfo()
    drawMax()
  }


  /**
    Draws a single pixel array
    */
  def drawPixelArray(index: Int, idx: Int, idy: Int): Unit = {

    val x_offset = idx*vizLength
    val y_offset = (idy*vizHeight + vizHeight/2)

    // for the wf array we ignore drawcall color request
    renderer.fillStyle = "orange"
    if(index == vizChannelIdx) {
      renderer.fillStyle = "yellow"
    }

    for(ii <- 0 until pixels(index).length){
      val min = pixels(index)(ii).yMin
      val max = pixels(index)(ii).yMax
      val height = max - min
      val start  = y_offset - max
      renderer.fillRect(x_offset + ii, start, 2, height)
    }

    renderer.fillStyle = "darkSlateGray"
    renderer.fillRect(x_offset, y_offset, x_offset + vizLength - 1, 2)
  }


  def drawPixelArrays(): Unit = {
    val windows = channelsWithCoordinates.zipWithIndex
    windows.foreach(window => drawPixelArray( mcsChannelMap.getMCSdataChannel(window._2), window._1._1, window._1._2))
  }



  canvas.onclick = { (e: dom.MouseEvent) =>
    import mcsChannelMap._
    val (x,y) = normalizeCanvasCoords(e.clientX.toInt, e.clientY.toInt)
    this.clickyX = x.toInt
    this.clickyY = y.toInt
    val xChannel = x/vizLength
    val yChannel = y/vizHeight
    val windows = channelsWithCoordinates.zipWithIndex.toMap

    // the one we see in the viz
    var newVizUserIdx = windows.lift((xChannel.toInt, yChannel.toInt)).getOrElse(vizUserIdx)
    say(s"old viz user idx $vizUserIdx")
    say(s"new viz user idx $newVizUserIdx")

    // Interal one
    vizChannelIdx = MCStoSHODAN(SHODANvizToMCS(newVizUserIdx))
    say(vizChannelIdx)

    if(newVizUserIdx != vizUserIdx){
      channelClickedHandler(vizChannelIdx)
      vizUserIdx = newVizUserIdx
    }
  }



  def normalizeCanvasCoords(x: Int, y: Int) = {
    val boundingBox = canvas.getBoundingClientRect()
    ((x - boundingBox.left) * (canvas.width / boundingBox.width),
      (y - boundingBox.top) * (canvas.height / boundingBox.height))
  }


  def drawMax(): Unit = {
  }


  def clear(): Unit = {
    renderer.fillStyle = "rgb(211, 211, 211)"
    renderer.fillRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)
    renderer.fillStyle = "grey"
    renderer.fillRect(vizLength, 0, canvas.width.toDouble - (vizLength*2), canvas.height.toDouble)
    renderer.fillRect(0, vizHeight, canvas.width.toDouble, canvas.height.toDouble - vizHeight*2)
  }


  def drawGrid(): Unit = {
    renderer.fillStyle = "black"
    renderer.fillRect(vizLength, 0,           vizLength*6, 4) // ----------
    renderer.fillRect(vizLength, vizHeight*8, vizLength*6, 4) // ----------

    renderer.fillRect(0,           0 + vizHeight, 4, vizHeight*6) // |
    renderer.fillRect(vizLength*8, 0 + vizHeight, 4, vizHeight*6) //          |

    (1 to 7).foreach{ x =>
      renderer.fillRect(0, x*vizHeight, vizLength*8, 4) // ---
      renderer.fillRect(x*vizLength, 0, 4, vizHeight*8) // |||
    }
  }


  def drawChannelInfo(): Unit = {
    channelsWithCoordinates.foreach{ case(x, y) =>
      val channelNo =
        if (y == 0)
          x
        else if (y == 7)
          6 + 6*8 + x
        else
          (6 + (y-1)*8 + x) + 1

      val channelString = s"[%02d]".format(channelNo - 1)
      renderer.fillStyle = "black"
      renderer.font = "24px Arial"
      renderer.fillText(channelString, x*vizLength + 26, y*vizHeight + 20)
    }
  }
}
