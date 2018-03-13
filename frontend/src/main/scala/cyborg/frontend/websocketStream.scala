package cyborg

import scala.scalajs.js
import scala.scalajs.js.typedarray.DataView

import org.scalajs.dom.raw._

import utilz._

object websocketStream {

  import params.webSocket._

  val wsProtocol = "ws"
  val rawDataWsUri = s"$wsProtocol://127.0.0.1:$dataPort"
  val agentWsUri = s"$wsProtocol://127.0.0.1:$agentPort"


  def createWaveformWs(controller: waveformVisualizer.WFVisualizerControl): Unit = {

    import params.waveformVisualizer.wfMsgSize

    say(s"creating new waveform websocket with $rawDataWsUri")
    val ws = new WebSocket(rawDataWsUri)
    say(s"created $ws")
    say(s"ws url was ${ws.url}")

    var counter = 0

    ws.onopen = (event: Event) => {
      say("opening waveform WebSocket. YOLO")
    }

    ws.binaryType = "arraybuffer"
    ws.onmessage = (event: MessageEvent) => {
      say("spam")
      val jsData = event.data.asInstanceOf[js.typedarray.ArrayBuffer]
      val memelord = new DataView(jsData)
      val buf: Array[Int] = Array.ofDim(wfMsgSize)
      for(ii <- 0 until wfMsgSize) {
        buf(ii) = memelord.getInt32(ii*4)
      }

      counter = counter + 1
      if(counter == 1000){
        counter = 0
        println(s"frontend dataqueue buffered size is ${controller.dataqueue.size}")
      }

      controller.dataqueue.enqueue(buf)
    }
  }


  def createAgentWsQueue(controller: Visualizer.VisualizerControl): Unit = {


    say(s"creating new agent websocket with $agentWsUri")
    val ws = new WebSocket(agentWsUri)
    say(s"created $ws")
    say(s"ws url was ${ws.url}")

    ws.onopen = (event: Event) => {
      say("opening agent WebSocket. YOLO")
    }

    ws.binaryType = "arraybuffer"
    ws.onmessage = (event: MessageEvent) => {
      say("got agent yo")
      val jsData = event.data.asInstanceOf[js.typedarray.ArrayBuffer]
      val memelord = new DataView(jsData)
      val buf: Array[Int] = Array.ofDim(4)
      for(ii <- 0 until 4) {
        buf(ii) = memelord.getInt32(ii*4)
      }
      val coord = wallAvoid.Coord(buf(0).toDouble, buf(1).toDouble)
      val agent = wallAvoid.Agent(coord, buf(2).toDouble/10000, buf(3))
      controller.newestAgent = agent
    }
  }
}
