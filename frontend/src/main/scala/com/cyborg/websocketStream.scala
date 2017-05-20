package com.cyborg

import fs2.Strategy
import fs2.Scheduler
import fs2.util.Async
import scodec.Codec
import scodec.bits.BitVector

import org.scalajs.dom

import org.scalajs.dom.raw._

object websocketStream {

  val wsProtocol = "ws"
  val wsUri = s"$wsProtocol://${dom.document.location.host}/channelData"

  implicit val IntCodec = scodec.codecs.int32
  implicit val IntVectorCodec = scodec.codecs.vectorOfN(scodec.codecs.int32, scodec.codecs.int32)

  implicit val strategy: Strategy  = fs2.Strategy.default
  implicit val scheduler: Scheduler = fs2.Scheduler.default

  def createWsQueue = {

    val inStreamQueueTask = fs2.async.unboundedQueue[fs2.Task,Vector[Int]]
    inStreamQueueTask flatMap { queue =>

      val task = fs2.Task.delay {

        val ws = new WebSocket(wsUri)

        ws.onopen = (event: Event) => {
          println("opening WebSocket. YOLO")
        }

        ws.onmessage = (event: MessageEvent) => {
          val bits = event.data.asInstanceOf[BitVector]
          val decoded = Codec.decode[Vector[Int]](bits).require
          queue.enqueue1(decoded.value).unsafeRunAsync(a => ())
        }
      }
      task
    }
  }

}
