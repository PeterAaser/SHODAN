package com.cyborg

import fs2.async.mutable.Topic
import org.scalajs.dom
import org.scalajs.dom.html
import scalajs.js
import scalatags.JsDom.all._
import org.scalajs.dom.raw.MouseEvent

import fs2._

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.document

import com.cyborg.wallAvoid.Agent


object testan extends js.JSApp {


  def main(): Unit = {

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



    document.getElementById("playground").appendChild(startSHODANButton)
    document.getElementById("playground").appendChild(connectAgentButton)
    document.getElementById("playground").appendChild(visualizeAgentButton)
    document.getElementById("playground").appendChild(connectWfButton)
    document.getElementById("playground").appendChild(visualizeWfButton)
    document.getElementById("playground").appendChild(glButton)

    document.getElementById("playground").appendChild(agentCanvas)
    document.getElementById("playground").appendChild(visualizerCanvas)


    // I have a stream of tokens from the user each corresponding to an action, often asynchronous.
    //   What I want is to have a pipe that looks something like this:


  }
}
