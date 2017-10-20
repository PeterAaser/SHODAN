package cyborg

import scala.scalajs.js.typedarray.TypedArrayBuffer
import scodec._
import scodec.bits._

import scala.scalajs.js

import org.scalajs.dom.raw._

import wallAvoid.Agent


object websocketStream {

  import sharedImplicits._
  import params.webSocket._

  val wsProtocol = "ws"
  val rawDataWsUri = s"$wsProtocol://127.0.0.1:$dataPort"
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
      val byteBuf = TypedArrayBuffer.wrap(jsData)
      println(byteBuf)
      val buf: Array[Int] = Array.ofDim(100)
      for(ii <- 0 until 100){
        val asInt =
          ((byteBuf.get((ii*4) + 3)) |
             (byteBuf.get((ii*4) + 2) << 8) |
             (byteBuf.get((ii*4) + 1) << 16) |
             (byteBuf.get((ii*4) + 0) << 24))
        buf(ii) = asInt
      }
      controller.dataqueue.enqueue(buf)
    }
  }


  def createAgentWsQueue(controller: Visualizer.VisualizerControl): Unit = {


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
      controller.newestAgent = decoded.value
    }
  }
}
