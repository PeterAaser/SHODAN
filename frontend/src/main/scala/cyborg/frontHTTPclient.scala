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

  def stopSHODAN: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/stop")
    req.send()
  }

  def startAgent: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/agent")
    req.send()
  }

  /**
    Opens a websocket to get the hottest new Agent data
    */
  def startAgentStream(cantvas: html.Canvas): Unit = {

    val controller = new Visualizer.VisualizerControl(cantvas, Agent(Coord(.0,.0),0,0))
    websocketStream.createAgentWsQueue(controller)
  }

  def startWaveformStream(cantvas: html.Canvas): Unit = {

    val controller = new waveformVisualizer.WFVisualizerControl(cantvas, new scala.collection.mutable.Queue())
    websocketStream.createWaveformWs(controller)
  }

  def startWf: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/wf")
    req.send()
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

  def dspTest: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/dsptest")
    req.send()
  }

  def dspSet: Unit = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/dspset")
    req.send()
  }

  def testDebugMsg: Unit = {
    val sizeReq = new dom.XMLHttpRequest()
    sizeReq.open("GET", "http://127.0.0.1:8080/info_waiting")
    sizeReq.onload = (e: dom.Event) => {
      println(e)
    }
    sizeReq.send()
  }
}
