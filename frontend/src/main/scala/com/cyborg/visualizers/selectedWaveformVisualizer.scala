package com.cyborg

import org.scalajs.dom
import org.scalajs.dom.html

object selectedWfVisualizer {

  class selectedWfController(
    canvas: html.Canvas,
    val dataqueue: scala.collection.mutable.Queue[Vector[Int]]) {

    import params.selectWfVisualizer._

    val renderer = canvas.getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]

    renderer.font = "16 comic sans"
    renderer.textAlign = "center"
    renderer.textBaseline = "middle"
    renderer.fillStyle = "yellow"

    canvas.width = vizLength

  }

}
