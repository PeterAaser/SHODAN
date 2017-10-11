package com.cyborg

import org.scalajs.dom.html
import scalajs.js
import scalatags.JsDom.all._
import org.scalajs.dom.raw.MouseEvent

import org.scalajs._
import scala.scalajs.js
import org.scalajs.dom.document


object testan {

  def main(args: Array[String]): Unit = {

    val agentCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
    val visualizerCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

    val startSHODANButton = button("start SHODAN").render
    startSHODANButton.onclick = (_: MouseEvent) => {
      println("SHODAN button clicked")
      frontIO.startSHODAN
    }

    val connectAgentButton = button("connect agent").render
    connectAgentButton.onclick = (_: MouseEvent) => {
      println("connect agent button clicked")
      frontIO.startAgent
    }

    val visualizeAgentButton = button("visualize agent").render
    visualizeAgentButton.onclick = (_: MouseEvent) => {
      println("visualize button clicked")
      frontIO.startAgentStream(agentCanvas)
    }

    val connectWfButton = button("connect waveforms").render
    connectWfButton.onclick = (_: MouseEvent) => {
      println("connect waveform button clicked")
      frontIO.startWF
    }

    val visualizeWfButton = button("visualize waveforms").render
    visualizeWfButton.onclick = (_: MouseEvent) => {
      println("visualize waveform button clicked")
      frontIO.startWaveformStream(visualizerCanvas)
    }

    val glButton = button("gl hf").render
    glButton.onclick = (_: MouseEvent) => {
      println("visualize waveform button clicked")
      val aa = new webgltest.webgltestController(visualizerCanvas)
      aa.test1()
    }

    val testDebugMessages = button("hurr").render
    testDebugMessages.onclick = (_: MouseEvent) => {
      println("the debug msg button clicked")
      val sizeReq = new dom.XMLHttpRequest()
      sizeReq.open("GET", "http://127.0.0.1:8080/info_waiting")
      sizeReq.onload = (e: dom.Event) => {
        println(e)
      }
      sizeReq.send()
    }

    val crash = button("stop_SHODAN").render
    crash.onclick = (_: MouseEvent) => {
      println("Stop SHODAN button clicked")
      frontIO.stopSHODAN
    }

    val startDBButton = button("From DB").render
    startDBButton.onclick = (_: MouseEvent) => {
      println("DB button clicked")
      frontIO.startDB
    }

    document.getElementById("playground").appendChild(startSHODANButton)
    document.getElementById("playground").appendChild(connectAgentButton)
    document.getElementById("playground").appendChild(visualizeAgentButton)
    document.getElementById("playground").appendChild(connectWfButton)
    document.getElementById("playground").appendChild(visualizeWfButton)
    document.getElementById("playground").appendChild(glButton)
    document.getElementById("playground").appendChild(testDebugMessages)
    document.getElementById("playground").appendChild(crash)
    document.getElementById("playground").appendChild(startDBButton)

    document.getElementById("playground").appendChild(agentCanvas)
    document.getElementById("playground").appendChild(visualizerCanvas)

  }
}
