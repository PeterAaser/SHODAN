package cyborg

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

    val visualizeWfButton = button("visualize waveforms").render
    val glButton = button("gl hf").render
    val testDebugMessages = button("hurr").render
    val crash = button("stop_SHODAN").render
    val startDBButton = button("From DB").render
    val startSHODANButton = button("start SHODAN").render
    val connectWfButton = button("connect waveforms").render
    val visualizeAgentButton = button("visualize agent").render
    val connectAgentButton = button("connect agent").render
    val DSPbutton = button("DSP thangs").render
    val DSPbutton2 = button("DSP thangs2").render

    /**
      Starts SHODAN and connects to MEAME.
      SHODAN is already running, so not really nescessary
      */
    startSHODANButton.onclick = (_: MouseEvent) =>
      frontHTTPclient.startSHODAN


    /**
      Creates and initializes an agent. If data is available
      this agent will start running even though visualization has not yet been started
      */
    connectAgentButton.onclick = (_: MouseEvent) =>
      frontHTTPclient.startAgent


    /**
      Visualizes data from a currently running agent
      */
    visualizeAgentButton.onclick = (_: MouseEvent) =>
      frontHTTPclient.startAgentStream(agentCanvas)


    /**
      Opens a websocket to get raw data for visualization from SHODAN
      Can be run both from database and from raw input
      */
    visualizeWfButton.onclick = (_: MouseEvent) =>
      frontHTTPclient.startWaveformStream(visualizerCanvas)


    // runs a gl test. fuck gl tbh
    glButton.onclick = (_: MouseEvent) => {
      println("visualize waveform button clicked")
      val aa = new webgltest.webgltestController(visualizerCanvas)
      aa.test1()
    }


    /**
      Does nothing at the moment. It's supposed to start querying for
      debug messages from scala to display it in a frontend.
      Possibly useful for things such as visualizing congestion et cetera
      */
    testDebugMessages.onclick = (_: MouseEvent) => {
      frontHTTPclient.testDebugMsg
    }


    /**
      Stops (crashes) SHODAN
      */
    crash.onclick = (_: MouseEvent) => {
      println("Stop SHODAN button clicked")
      frontHTTPclient.crashSHODAN
    }


    /**
      Starts running data from SHODANs database
      */
    startDBButton.onclick = (_: MouseEvent) => {
      println("DB button clicked")
      frontHTTPclient.startDB
    }


    // Self-evident
    DSPbutton.onclick = (_: MouseEvent) =>
      frontHTTPclient.dspTest

    DSPbutton2.onclick = (_: MouseEvent) =>
      frontHTTPclient.dspTest


    document.getElementById("playground").appendChild(startSHODANButton)
    document.getElementById("playground").appendChild(connectAgentButton)
    document.getElementById("playground").appendChild(visualizeAgentButton)
    document.getElementById("playground").appendChild(connectWfButton)
    document.getElementById("playground").appendChild(visualizeWfButton)
    document.getElementById("playground").appendChild(glButton)
    document.getElementById("playground").appendChild(testDebugMessages)
    document.getElementById("playground").appendChild(crash)
    document.getElementById("playground").appendChild(startDBButton)
    document.getElementById("playground").appendChild(DSPbutton)
    document.getElementById("playground").appendChild(DSPbutton2)

    document.getElementById("playground").appendChild(DSPbutton2)

    document.getElementById("playground").appendChild(agentCanvas)
    document.getElementById("playground").appendChild(visualizerCanvas)

  }
}
