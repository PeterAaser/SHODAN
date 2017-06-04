package com.cyborg

import org.scalajs.dom
import org.scalajs.dom.html
import scalajs.js
import scalatags.JsDom.all._

import fs2._

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.document

import com.cyborg.wallAvoid.Agent


object testan extends js.JSApp {

  @js.native
  trait EventName extends js.Object {
    type EventType <: dom.Event
  }

  object EventName {
    def apply[T <: dom.Event](name: String): EventName { type EventType = T } =
      name.asInstanceOf[EventName { type EventType = T }]

    val onmousedown = apply[dom.MouseEvent]("onmousedown")
  }

  @js.native
  trait ElementExt extends js.Object {
    def addEventListener(name: EventName)(
      f: js.Function1[name.EventType, _]): Unit
  }

  def main(): Unit = {
    val paragraph = dom.document.createElement("p")
    paragraph.innerHTML = "<strong>It werks!</strong>"
    dom.document.getElementById("playground").appendChild(paragraph)

    val p = paragraph.asInstanceOf[ElementExt]

    val cantvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

    document.getElementById("playground").appendChild(cantvas)

    val aButton = button("hi").render
    aButton.onclick = (_: org.scalajs.dom.raw.MouseEvent) => {
      import frontendImplicits._

      frontHTTPclient.pingShodanServer.unsafeRunAsync(_ => () )

      val sink: Sink[Task,Agent] = Visualizer.visualizerControlSink[Task](cantvas)
      var v = 0
      for (i <- 0 until 100000000){
        v = v + i
        if(i%1000 == 0)
          println(v)
      }
      println(v)

      val queue = fs2.async.unboundedQueue[Task,Agent]
      val thing = Stream.eval(queue) flatMap { queue =>
        val wsInstream = websocketStream.createAgentWsQueue(queue)
        Stream.eval(wsInstream).mergeDrainL(queue.dequeue.through(sink))
      }
      thing.run.unsafeRunAsync( _ => () )
    }




    dom.document.getElementById("playground").appendChild(aButton)

    val cantvas2: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
    val renderer2 =
      cantvas2.getContext("2d")
        .asInstanceOf[dom.CanvasRenderingContext2D]

    val canvasControl2 = new com.cyborg.waveformVisualizer.WFVisualizerControl(cantvas2)

    document.getElementById("playground").appendChild(cantvas2)

  }
}
