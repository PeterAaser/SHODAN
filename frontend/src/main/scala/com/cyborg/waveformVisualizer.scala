package com.cyborg

import org.scalajs.dom
import org.scalajs.dom.html

object waveformVisualizer {

  class WFVisualizerControl(
    canvas: html.Canvas,
    val dataqueue: scala.collection.mutable.Queue[Vector[Int]]) {

    import params.waveformVisualizer._

    canvas.width = vizLength*12
    canvas.height = vizLength*12

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
      if(!dataqueue.isEmpty){
        gogo(dataqueue.dequeue())
      }
    }

    def gogo(data: Vector[Int]): Unit = {
      val chopped = data.grouped(groupSize).zipWithIndex
      chopped.foreach(λ => drawToPixelArray(λ._1, λ._2))
      renderer.fillStyle = "grey"
      renderer.fillRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)
      renderer.fillStyle = "yellow"
      drawPixelArrays()
    }


    // Takes a new datasegment and appends to the pixelarray
    def drawToPixelArray(data: Vector[Int], index: Int): Unit = {
      val (remainder, _) = pixels(index) splitAt(vizLength - reducedSegmentLength)
      pixels(index) = (normalize(data).toArray ++ remainder)
    }


    // Does what it says on the tin
    def normalize(data: Vector[Int]): Vector[Int] = {
      data.map( dataPoint =>
        {
          val normalized = {
            if((dataPoint > maxVal) || (dataPoint < -maxVal))
              0
            else
              dataPoint
          }
          (normalized*vizHeight)/(maxVal*2)
      })
    }


    // Draws a single graph
    def drawPixelArray(index: Int, idx: Int, idy: Int): Unit = {

      for(ii <- 0 until pixels(index).length){
        renderer.fillRect(ii + (idx*vizLength*2.0), (idy + 2)*vizHeight*2.0, 1, pixels(index)(ii))
      }
    }

    def drawPixelArrays(): Unit = {
      val windows = channelsWithCoordinates.zipWithIndex
      windows.foreach(window => drawPixelArray(window._2, window._1._1, window._1._2))
    }
  }
}
