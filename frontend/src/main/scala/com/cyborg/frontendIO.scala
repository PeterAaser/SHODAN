package com.cyborg

import wallAvoid._
import org.scalajs.dom.html

object frontIO {

  /**
    I AM SHODAN
    */
  def startSHODAN(): Unit = {
    println("sending start SHODAN http")
    frontHTTPclient.startShodanServer.unsafeRunAsync(_ => () )
  }


  def startAgent(): Unit = {
    println("sending start Agent http")
    frontHTTPclient.startAgentServer.unsafeRunAsync(_ => () )
  }


  def startWF(): Unit = {
    println("sending start WF http")
    frontHTTPclient.startWfServer.unsafeRunAsync(_ => () )
  }

  def stopSHODAN(): Unit = {
    println("sending stop SHODAN http")
    frontHTTPclient.crashSHODAN.unsafeRunAsync(_ => () )
  }

  def startDB(): Unit = {
    println("sending DB http")
    frontHTTPclient.runFromDB.unsafeRunAsync(_ => () )
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
}
