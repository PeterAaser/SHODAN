package com.cyborg

import fs2._
import scala.scalajs.js.typedarray.TypedArrayBuffer
import scodec._
import scodec.bits._

import scala.scalajs.js

import org.scalajs.dom.raw._

import wallAvoid.Agent


object websocketStream {

  import sharedImplicits._
  import frontendImplicits._
  import params.webSocket._

  val wsProtocol = "ws"
  val rawDataWsUri = s"$wsProtocol://127.0.0.1:$dataPort"
  val textWsUri = s"$wsProtocol://127.0.0.1:$textPort"
  val agentWsUri = s"$wsProtocol://127.0.0.1:$agentPort"


  def createWaveformWs(controller: waveformVisualizer.WFVisualizerControl): Unit = {

    println(s"creating new waveform websocket with $rawDataWsUri")
    val ws = new WebSocket(rawDataWsUri)
    println(s"created $ws")
    println(s"ws url was ${ws.url}")


    ws.onopen = (event: Event) => {
      println("opening waveform WebSocket. YOLO")
    }

    ws.binaryType = "arraybuffer"
    ws.onmessage = (event: MessageEvent) => {
      val jsData = event.data.asInstanceOf[js.typedarray.ArrayBuffer]
      val jsData2 = TypedArrayBuffer.wrap(jsData)
      val bits: BitVector = BitVector(jsData2)
      val decoded = Codec.decode[Vector[Int]](bits).require
      controller.dataqueue.enqueue(decoded.value)
      // controller.gogo(decoded.value)
    }
  }


  def createAgentWsQueue(queue: fs2.async.mutable.Queue[Task,Agent]): Task[Unit] = {

    val webSocketTask = {
      val task = fs2.Task.delay {

        println(s"creating new agent websocket with $agentWsUri")
        val ws = new WebSocket(agentWsUri)
        println(s"created $ws")
        println(s"ws url was ${ws.url}")

        ws.onopen = (event: Event) => {
          println("opening agent WebSocket. YOLO")
        }

        ws.binaryType = "arraybuffer"
        ws.onmessage = (event: MessageEvent) => {
          val jsData = event.data.asInstanceOf[js.typedarray.ArrayBuffer]
          val jsData2 = TypedArrayBuffer.wrap(jsData)
          val bits: BitVector = BitVector(jsData2)
          val decoded = Codec.decode[Agent](bits).require
          queue.enqueue1(decoded.value).unsafeRunAsync(a => ())
        }
      }
      task
    }
    webSocketTask
  }
}
