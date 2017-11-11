package cyborg

import scala.scalajs.js.typedarray.{ DataView, TypedArrayBuffer }
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

    var counter = 0

    ws.onopen = (event: Event) => {
      println("opening waveform WebSocket. YOLO")
    }

    ws.binaryType = "arraybuffer"
    ws.onmessage = (event: MessageEvent) => {
      val jsData = event.data.asInstanceOf[js.typedarray.ArrayBuffer]
      val memelord = new DataView(jsData)
      val buf: Array[Int] = Array.ofDim(1200)
      for(ii <- 0 until 1200) {
        buf(ii) = memelord.getInt32(ii*4)
      }

      println(counter)
      counter = counter + 1
      if(counter == 100){
        counter = 0
        println("frontend wf received")
        println(s"frontend dataqueue buffered size is ${controller.dataqueue.size}")
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
