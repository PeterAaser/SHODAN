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

  // hardcoded
  val rawDataPort = 9090
  val textPort = 9091
  val agentPort = 9092

  val wsProtocol = "ws"
  val rawDataWsUri = s"$wsProtocol://127.0.0.1:$rawDataPort"
  val textWsUri = s"$wsProtocol://127.0.0.1:$textPort"
  val agentWsUri = s"$wsProtocol://127.0.0.1:$agentPort"


  def createWsQueue(queue: fs2.async.mutable.Queue[Task,Vector[Int]]): Task[Unit] = {

    val webSocketTask = {
      val task = fs2.Task.delay {

        println("creating new websocket")
        val ws = new WebSocket(rawDataWsUri)
        println(s"created $ws")
        println(ws.url)


        ws.onopen = (event: Event) => {
          println("opening WebSocket. YOLO")
        }

        ws.binaryType = "arraybuffer"
        ws.onmessage = (event: MessageEvent) => {
          val jsData = event.data.asInstanceOf[js.typedarray.ArrayBuffer]
          val jsData2 = TypedArrayBuffer.wrap(jsData)
          val bits: BitVector = BitVector(jsData2)
          val decoded = Codec.decode[Vector[Int]](bits).require
          queue.enqueue1(decoded.value).unsafeRunAsync(a => ())
        }
      }
      task
    }
    webSocketTask
  }


  def createAgentWsQueue(queue: fs2.async.mutable.Queue[Task,Agent]): Task[Unit] = {

    val webSocketTask = {
      val task = fs2.Task.delay {

        println("creating new websocket")
        val ws = new WebSocket(agentWsUri)
        println(s"created $ws")
        println(ws.url)

        ws.onopen = (event: Event) => {
          println("opening WebSocket. YOLO")
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


  def drawChannelStreams(channels: Int, controller: waveformVisualizer.WFVisualizerControl): Task[Unit] = {

    val queueTask = fs2.async.unboundedQueue[Task,Vector[Int]]
    val drawTask: Stream[Task,Unit] = Stream.eval(queueTask) flatMap ( queue =>
      {
        // enqueue data
        val enqueueTask = createWsQueue(queue)

        // hardcoded
        val channelStreams = utilz.alternator(queue.dequeue, 4, 60, 1000)
        val mapped = channelStreams.flatMap(
          streams => controller.gogo[Task](streams.map(_.through(utilz.chunkify)).toList))

        Stream.eval(enqueueTask) merge mapped
      })
    drawTask.run
  }
}
