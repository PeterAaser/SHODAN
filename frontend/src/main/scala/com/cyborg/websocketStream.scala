package com.cyborg

import fs2.Strategy
import fs2.Scheduler
import fs2._
import fs2.util.Async
import scala.scalajs.js.typedarray.TypedArrayBuffer
import scodec._
import scodec.bits._
import codecs._

import org.scalajs.dom
import org.scalajs._
import scala.scalajs.js

import org.scalajs.dom.raw._

object websocketStream {

  val wsProtocol = "ws"
  val wsUri = s"$wsProtocol://127.0.0.1:9090"

  implicit val IntCodec = scodec.codecs.int32
  implicit val IntVectorCodec = scodec.codecs.vectorOfN(scodec.codecs.int32, scodec.codecs.int32)

  implicit val strategy: Strategy  = fs2.Strategy.default
  implicit val scheduler: Scheduler = fs2.Scheduler.default

  def createWsQueue(queue: fs2.async.mutable.Queue[Task,Vector[Int]]): Task[Unit] = {

    val webSocketTask = {
      val task = fs2.Task.delay {

        println("creating new websocket")
        val ws = new WebSocket(wsUri)
        println(s"created $ws")
        println(ws.url)


        ws.onopen = (event: Event) => {
          println("opening WebSocket. YOLO")
        }

        ws.binaryType = "arraybuffer"
        ws.onmessage = (event: MessageEvent) => {
          println("\n\n\n------------------------")
          val jsData = event.data.asInstanceOf[js.typedarray.ArrayBuffer]
          val jsData2 = TypedArrayBuffer.wrap(jsData)
          val bits: BitVector = BitVector(jsData2)
          val decoded = Codec.decode[Vector[Int]](bits).require
          println(s"decode result: $decoded")
          queue.enqueue1(decoded.value).unsafeRunAsync(a => ())
        }
      }
      task
    }
    webSocketTask
  }

  def drawChannelStreams(channels: Int, controller: waveformVisualizer.WFVisualizerControl): Task[Unit] = {

    val queueTask = fs2.async.unboundedQueue[Task,Vector[Int]]
    val memer: Stream[Task,Unit] = Stream.eval(queueTask) flatMap ( queue =>
      {
        // enqueue data
        val uhh = Stream.eval(createWsQueue(queue))

        // hardcoded
        val erm = utilz.alternator(queue.dequeue, 10 ,60)
        val fug = erm.flatMap( streams =>
          {
            val uhh = controller.gogo[Task](streams.map(_.through(utilz.chunkify)).toList)
            uhh
          })
        uhh merge fug
      })
    memer.run
  }

}
