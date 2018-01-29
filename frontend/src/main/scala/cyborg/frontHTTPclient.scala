package cyborg

import org.scalajs._
import org.scalajs.dom.html
import wallAvoid._

object frontHTTPclient {

  // refactor me
  def startSHODAN: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/connect")
    req.onload = (e: dom.Event) => {
      println(e)
      println("nice meme..")
      println(req.statusText)
    }
    req.send()
  }

  def startAgentStream(cantvas: html.Canvas): Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/agent")
    req.send()

    val controller = new Visualizer.VisualizerControl(cantvas, Agent(Coord(.0,.0),0,0))
    websocketStream.createAgentWsQueue(controller)
  }

  def startWaveformStream(cantvas: html.Canvas): Unit = {
    val controller = new waveformVisualizer.WFVisualizerControl(cantvas, new scala.collection.mutable.Queue())
    websocketStream.createWaveformWs(controller)
  }

  def startDB: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/db")
    req.send()
  }

  def crashSHODAN: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/fuckoff")
    req.send()
  }

  def dspStimTest: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/dspstimtest")
    req.send()
  }

  def dspUploadTest: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/dspuploadtest")
    req.send()
  }

  def dspBarf: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/barf")
    req.send()
  }

  def reset: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/reset_dsp_debug")
    req.send()
  }

  def tests: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/test_stuff")
    req.send()
  }

  def startRecording: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/record_start")
    req.send()
  }

  def stopRecording: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/record_stop")
    req.send()
  }
}
