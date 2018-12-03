package cyborg

import org.scalajs.dom
import org.scalajs.dom.html
import frontilz._

import cyborg.frontend.services.rpc.RPCService

object waveformVisualizer {

  class WFVisualizerControl(canvas: html.Canvas,
                            dataqueue: scala.collection.mutable.Queue[Array[Int]]) {

  var maxVal = 40800

    import params.waveformVisualizer._

    canvas.width = vizLength*8 + 4
    canvas.height = vizHeight*8 + 4

    say(canvas.height)
    say(canvas.width)

    var testan = 0

    say("attempting to get context")
    say(s"canvas: $canvas")
    val renderer = canvas.getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]
    say("dunnit")

    renderer.font = "16 comic sans"
    renderer.textAlign = "center"
    renderer.textBaseline = "middle"
    renderer.fillStyle = "yellow"

    type Color = Vec
    val Color = Vec
    case class Vec(x: Double, y: Double, z: Double)

    val pixels = Array.ofDim[Int](60,vizLength)
    val imageData = renderer.createImageData(1200, 1200)


    /**
      We use a "fat plus shape" just like MCS suite
      This necessitates some juggling to get the correct indexes for the
      drawing sinks

        # # # # # #
      # # # # # # # #
      # # # # # # # #
      # # # # # # # #
      # # # # # # # #
      # # # # # # # #
      # # # # # # # #
        # # # # # #

      Some tuple rearranging, but conceptually simple, just follow the types
      */


    val topRowWithCoords: List[(Int, Int)] =
      (1 to 6).map(λ => (λ,0)).toList

    val botRowWithCoords: List[(Int, Int)] =
      (1 to 6).map(λ => (λ,7)).toList

    val middleRowsWithCoords = (0 until 48).sliding(8,8)
      .map( row => row zip (0 to 7) ).toList.transpose
      .map( column => column zip (1 to 6) )
      .map(_.map(λ => (λ._1._2, λ._2)))
      .transpose
      .flatten

    val channelsWithCoordinates: List[(Int,Int)] =
      topRowWithCoords ::: middleRowsWithCoords ::: botRowWithCoords


    var groupSize = 10
    var running = false
    var num = 0

    // TODO what in the name of fuck it this running variable???
    scalajs.js.timers.setInterval(25) {
      if(!running){
        running = true
        if(dataqueue.size > 500){
          println("UH OH DATA QUEUE OVERFLOWING")
          println(dataqueue.size)
        }
        if(dataqueue.size > 0){
          gogo(dataqueue.dequeue)
        }
        running = false
      }
    }

    def gogo(data: Array[Int]): Unit = {
      groupSize = data.size/60
      clear()
      val chopped = data.grouped(groupSize).zipWithIndex.toList
      chopped.foreach(λ => drawToPixelArray(λ._1, λ._2 % 60))
      renderer.fillStyle = "yellow"
      drawPixelArrays()
      drawGrid()
      drawChannelInfo()
    }


    // Takes a new datasegment and appends to the pixelarray
    def drawToPixelArray(data: Array[Int], index: Int): Unit = {
      val (remainder, _) = pixels(index) splitAt(vizLength - groupSize)
      pixels(index) = (normalize(data).reverse.toArray ++ remainder)
    }


    // Does what it says on the tin
    def normalize(data: Array[Int]): Array[Int] = {
      data.map( dataPoint =>
        {
          val normalized = {
            if(dataPoint > maxVal){
              maxVal
            }
            else if (dataPoint < -maxVal) {
              -maxVal
            }
            else
              dataPoint
          }
          (normalized*vizHeight)/(maxVal*2)
      })
    }


    // Draws a single graph
    def drawPixelArray(index: Int, idx: Int, idy: Int): Unit = {

      for(ii <- 0 until pixels(index).length){
        val x_offset = idx*vizLength
        val y_offset = idy*vizHeight + vizHeight/2
        val bar = pixels(index)(ii)
        if(((bar-1) <= -maxVal/2) || ((bar+1) >= maxVal/2)){
          renderer.fillStyle = "green"
        }
        renderer.fillRect(ii + x_offset, y_offset, 2, pixels(index)(ii))
        renderer.fillStyle = "yellow"
      }
    }

    def drawPixelArrays(): Unit = {
      val windows = channelsWithCoordinates.zipWithIndex
      windows.foreach(window => drawPixelArray( mcsChannelMap.getMCSchannel(window._2), window._1._1, window._1._2))
    }

    println(canvas.width)
    println(canvas.height)

    clear()
    drawGrid()
    drawChannelInfo()

    def clear(): Unit = {
      renderer.fillStyle = "rgb(211, 211, 211)"
      renderer.fillRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)
      renderer.fillStyle = "grey"
      renderer.fillRect(vizLength, 0, canvas.width.toDouble - vizLength*2, canvas.height.toDouble)
      renderer.fillRect(0, vizHeight, canvas.width.toDouble, canvas.height.toDouble - vizHeight*2)
    }

    def drawGrid(): Unit = {

      renderer.fillStyle = "black"
      renderer.fillRect(vizLength, 0,           vizLength*6, 4) // ----------
      renderer.fillRect(vizLength, vizHeight*8, vizLength*6, 4) // ----------


      renderer.fillRect(0,           0 + vizHeight, 4, vizHeight*6) // |
      renderer.fillRect(vizLength*8, 0 + vizHeight, 4, vizHeight*6) //          |

      (1 to 7).foreach{ λ =>
        renderer.fillRect(0, λ*vizHeight, vizLength*8, 4) // ---
        renderer.fillRect(λ*vizLength, 0, 4, vizHeight*8) // |||
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
}
