package cyborg

import org.scalajs.dom.html
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._
import org.scalajs.dom.raw.MouseEvent
import org.scalajs.dom

import org.scalajs.dom.document
import japgolly.scalajs.react._
import japgolly.scalajs.react.ReactDOM
import japgolly.scalajs.react.vdom.html_<^._

import org.scalajs.dom.document

import chandu0101.scalajs.react.components.WithAsyncScript
import chandu0101.scalajs.react.components.materialui._

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

object hurr {

  def main(args: Array[String]): Unit = {

    timerTest.Timer().renderIntoDOM(document.getElementById("playground"))

    val agentCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
    val visualizerCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

    val visualizeWfButton = button("visualize waveforms").render
    val crash = button("stop_SHODAN").render
    val startDBButton = button("From DB").render
    val startSHODANButton = button("start SHODAN").render
    val visualizeAgentButton = button("visualize agent").render
    val connectAgentButton = button("connect agent").render


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


    document.getElementById("playground").appendChild(startSHODANButton)
    document.getElementById("playground").appendChild(connectAgentButton)
    document.getElementById("playground").appendChild(visualizeAgentButton)
    document.getElementById("playground").appendChild(visualizeWfButton)
    document.getElementById("playground").appendChild(crash)
    document.getElementById("playground").appendChild(startDBButton)


    document.getElementById("playground").appendChild(agentCanvas)
    document.getElementById("playground").appendChild(visualizerCanvas)
  }
}
