package cyborg

import org.scalajs.dom.html
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._
import org.scalajs.dom.raw.MouseEvent
import org.scalajs.dom

import org.scalajs.dom.document
import org.scalajs.dom.document

object hurr {

  def main_(args: Array[String]): Unit = {

    val agentCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
    val visualizerCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

    val startMEAME = button("MEAME").render
    val startDB = button("DB rec 2").render
    val startDBNewest = button("Newest DB").render
    val startRecord = button("start recording").render
    val stopRecord = button("stop recording").render
    val crash = button("stop SHODAN").render
    val testStimButton = button("test stim").render
    val testUploadButton = button("upload stimulus test").render
    val barf = button("DSP barf").render
    val reset = button("DSP reset").render
    val tests = button("\"\"\"Test\"\"\"").render

    /**
      Starts SHODAN and connects to MEAME.
      SHODAN is already running, so not really nescessary
      */
    startMEAME.onclick = (_: MouseEvent) => {
      frontHTTPclient.startWaveformStream(visualizerCanvas)
      frontHTTPclient.startAgentStream(agentCanvas)
      frontHTTPclient.startSHODAN
    }

    /**
      Starts running data from SHODANs database
      */
    startDB.onclick = (_: MouseEvent) => {
      println("DB button clicked")
      frontHTTPclient.startWaveformStream(visualizerCanvas)
      frontHTTPclient.startAgentStream(agentCanvas)
      frontHTTPclient.startDB
    }

    startDBNewest.onclick = (_: MouseEvent) => {
      println("DB button clicked")
      frontHTTPclient.startWaveformStream(visualizerCanvas)
      frontHTTPclient.startAgentStream(agentCanvas)
      frontHTTPclient.startDBNewest
    }

    startRecord.onclick = (_: MouseEvent) => {
      println("Starting a database recording")
      document.getElementById("playground").replaceChild(stopRecord, startRecord)
      frontHTTPclient.startRecording
    }

    stopRecord.onclick = (_: MouseEvent) => {
      println("Stopping database recording")
      document.getElementById("playground").replaceChild(startRecord, stopRecord)
      frontHTTPclient.stopRecording
    }


    /**
      Stops (crashes) SHODAN
      */
    crash.onclick = (_: MouseEvent) => {
      println("Stop SHODAN button clicked")
      frontHTTPclient.crashSHODAN
    }


    /**
      Extras and debug
      */
    testStimButton.onclick = (_: MouseEvent) => {
      println("Running DSP stim request test")
      frontHTTPclient.dspStimTest
    }

    testUploadButton.onclick = (_: MouseEvent) => {
      println("Attempting to upload stimulus")
      frontHTTPclient.dspUploadTest
    }

    barf.onclick = (_: MouseEvent) => {
      println("barfing debug")
      frontHTTPclient.dspBarf
    }

    reset.onclick = (_: MouseEvent) => {
      println("resetting debug")
      frontHTTPclient.reset
    }

    tests.onclick = (_: MouseEvent) => {
      println("resetting debug")
      frontHTTPclient.tests
    }

    document.getElementById("playground").appendChild(startMEAME)
    document.getElementById("playground").appendChild(startDB)
    document.getElementById("playground").appendChild(startDBNewest)
    document.getElementById("playground").appendChild(startRecord)
    document.getElementById("playground").appendChild(crash)
    document.getElementById("playground").appendChild(testStimButton)
    document.getElementById("playground").appendChild(barf)
    document.getElementById("playground").appendChild(reset)
    document.getElementById("playground").appendChild(tests)
    document.getElementById("playground").appendChild(testUploadButton)


    document.getElementById("playground").appendChild(agentCanvas)
    document.getElementById("playground").appendChild(visualizerCanvas)
  }
}
