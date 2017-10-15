package com.cyborg

import org.scalajs.dom
import org.scalajs.dom.html

object waveformVisualizer {

  class WFVisualizerControl(
    canvas: html.Canvas,
    val dataqueue: scala.collection.mutable.Queue[Array[Int]]) {

    import params.waveformVisualizer._

    canvas.width = vizLength*8 + 4
    canvas.height = vizHeight*8 + 4

    var testan = 0

    val renderer = canvas.getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]

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


        _ _ o o _ _
      _ x _ o o _ x _
      _ _ _ o o _ _ _
      o o o o o o o o
      o o o o o o o o
      _ _ _ o o _ _ _
      _ x _ o o _ x _
        _ _ o o _ _

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
      .flatten


    val channelsWithCoordinates: List[(Int,Int)] =
      topRowWithCoords ::: botRowWithCoords ::: middleRowsWithCoords


    val groupSize = wfMsgSize/60

    scalajs.js.timers.setInterval(70) {
      if(dataqueue.size > 500){
        println(dataqueue.size)
      }
      if(dataqueue.size > 12){
        val buf: Array[Array[Int]] = Array.ofDim(12)
        for(i <- 0 until 12){
          buf(i) = dataqueue.dequeue()
        }

        gogo(buf.flatten)
      }
      else{
        println(dataqueue.size)
      }
    }

    def gogo(data: Array[Int]): Unit = {
      clear()
      val chopped = data.grouped(groupSize).zipWithIndex
      chopped.foreach(λ => drawToPixelArray(λ._1, λ._2))
      renderer.fillStyle = "yellow"
      drawPixelArrays()
      drawGrid()
    }


    // Takes a new datasegment and appends to the pixelarray
    def drawToPixelArray(data: Array[Int], index: Int): Unit = {
      val (remainder, _) = pixels(index) splitAt(vizLength - reducedSegmentLength)
      pixels(index) = (normalize(data).toArray ++ remainder)
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
      windows.foreach(window => drawPixelArray(window._2, window._1._1, window._1._2))
    }

    println(canvas.width)
    println(canvas.height)

    clear()
    drawGrid()

    def clear(): Unit = {
      renderer.fillStyle = "grey"
      renderer.fillRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)
    }

    def drawGrid(): Unit = {

      renderer.fillStyle = "black"
      // renderer.fillRect(x, y, w, h)
      renderer.fillRect(vizLength, 0,           vizLength*6, 4) // ----------
      renderer.fillRect(vizLength, vizHeight*8, vizLength*6, 4) // ----------


      renderer.fillRect(0,           0 + vizHeight, 4, vizHeight*6) // |
      renderer.fillRect(vizLength*8, 0 + vizHeight, 4, vizHeight*6) //          |

      (1 to 7).foreach{ λ =>
        renderer.fillRect(0, λ*vizHeight, vizLength*8, 4) // ---
        renderer.fillRect(λ*vizLength, 0, 4, vizHeight*8) // |||
      }
    }
  }
}
