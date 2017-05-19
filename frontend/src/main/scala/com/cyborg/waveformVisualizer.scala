package com.cyborg

import fs2.Strategy
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs._
import spinoco.fs2.http

import fs2._
import fs2.util.syntax._
import spinoco.fs2.http
import http._
import http.websocket._
import spinoco.protocol.http.header._
import spinoco.protocol.http._
import spinoco.protocol.http.header.value._

import scala.concurrent.duration._
import scala.scalajs.js.JSApp

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js.timers._


import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom
import org.scalajs.dom.html
import scala.util.Random
import scalajs.js

object waveformVisualizer {

  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val scheduler: Scheduler = Scheduler.fromFixedDaemonPool(8, "memer")
  implicit val strategy: Strategy = Strategy.fromFixedDaemonPool(8, "fugger")

  implicit val IntCodec = scodec.codecs.int32
  implicit val IntVectorCodec = scodec.codecs.vectorOfN(scodec.codecs.int32, scodec.codecs.int32)

  // A pipe that ignores input and outputs stuff from inStream
  // def wsPipe(inStream: Stream[Task,Vector[Int]]):
  //     Pipe[Task, Frame[Vector[Int]], Frame[Vector[Int]]] = { inbound =>

  //   val output = inStream
  //     .through(_.map ( λ => { println(s" converting input to frame :--DDd "); λ }))
  //     .through(_.map(Frame.Binary(_)))
  //   inbound.mergeDrainL(output)
  // }

  // val inStream = Stream.emit(Vector(1,2,3))

  // http.client[Task]().flatMap { client =>
  //   val request = WebSocketRequest.ws("127.0.0.1", 9090, "/")
  //   client.websocket(request, wsPipe(inStream)).run
  // }.unsafeRun()

  class WFVisualizerControl(canvas: html.Canvas) {

    // hardcoded
    val vizHeight = 60
    val vizLength = 200
    val pointsPerSec = 40000

    canvas.width = vizLength*8
    canvas.height = vizHeight*8


    renderer.font = "16 comic sans"
    renderer.textAlign = "center"
    renderer.textBaseline = "middle"

    type Color = Vec
    val Color = Vec
    case class Vec(x: Double, y: Double, z: Double)

    val renderer = canvas.getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]

    def channelStreams[F[_]]: List[Stream[F,Int]] = ???

    def doThing[F[_]](stream: List[Stream[F,Int]]): Stream[F,Unit] = {


      /**
        We use a "fat plus shape" just like MCS suite
        This necessitates some juggling to get the correct indexes for the
        drawing sinks

          # # # # # #
        # # # # # # # #
        # # # # # # # #
        # # # # # # # #
        # # # # # # # #
        # # # # # # # #
        # # # # # # # #
          # # # # # #

        Some tuple rearranging, but conceptually simple, just follow the types 
       */
      val topRowWithCoords: List[(Stream[F, Int], (Int, Int))] =
        (channelStreams.take(6)) zip (1 to 6).map(λ => (λ,0))

      val botRowWithCoords: List[(Stream[F, Int], (Int, Int))] =
        (channelStreams.takeRight(6)) zip (1 to 6).map(λ => (λ,0))

      val middleRowsWithCoords = (channelStreams.drop(6).dropRight(6)).sliding(8,8)
        .map( row => row zip (0 to 7) ).toList.transpose
        .map( column => column zip (1 to 6) )
        .map(_.map(λ => (λ._1._1, (λ._1._2, λ._2))))
        .flatten


      val channelsWithCoordinates: List[(Stream[F,Int], (Int,Int))] =
        topRowWithCoords ::: botRowWithCoords ::: middleRowsWithCoords

      val channelsWithDrawSinks: List[(Stream[F,Int], Sink[F,Int])] =
        channelsWithCoordinates.map(λ => (λ._1, drawSink[F](λ._2._1 * vizLength, λ._2._2 * vizHeight)))

      val channelTasks: List[Stream[F,Unit]] =
        channelsWithDrawSinks.map(λ => λ._1.through(λ._2).drain)

      val drawStream = Stream.emits(channelTasks).covary[F].flatMap(λ => λ)
      drawStream
    }


    // TODO this thing just YOLO draws, should be handled in a task?
    def drawSink[F[_]](x: Int, y: Int): Sink[F,Int] = {

      // local to this sink. Fuck javascript. No int type, really? REALLY????
      val imageData = renderer.createImageData(vizLength.toDouble, vizHeight.toDouble)
      def drawMe = renderer.putImageData(imageData, x.toDouble, y.toDouble)

      def go(previous: Vector[Int]): Handle[F,Int] => Pull[F,Int,Unit] = h => {
        h.await flatMap {
          case (chunk, h) => {
            val len = chunk.size
            val next = chunk.toVector ++ previous.dropRight(len)

            // TODO Unfuckulate this
            Pull.output1({draw(next, imageData); drawMe; 1}) >> go(next)(h)
          }
        }
      }
      _.through(downSampler(pointsPerSec/vizLength)).pull(go(Vector.fill(200)(0))).drain
    }


    def downSampler[F[_]](blockSize: Int): Pipe[F,Int,Int] = {
      def go: Handle[F,Int] => Pull[F,Int,Unit] = h => {
        h.awaitN(blockSize) flatMap {
          case (chunks, h) => {
            val waveform = chunks.map(_.toList).flatten
            val smallest = waveform.min
            val largest = waveform.max
            Pull.output1(if (math.abs(smallest) < largest) largest else smallest) >> go(h)
          }
        }
      }
      _.pull(go)
    }

    def draw(points: Vector[Int], imageData: dom.raw.ImageData): dom.raw.ImageData = {

      def makeRow(data: Int) = Vector.fill(data)(1) ++ Vector.fill(200-data)(0)
      def makeImg(data: Vector[Int]) = data.map(makeRow(_))

      def getIdx(x: Int, y: Int): Int =
        ((y*200) + x)*4

      val muhColor: Color = Vec(100.0, 100.0, 100.0)

      def plot(data: List[Vector[Int]]): Unit = {
        for(xx <- 0 until 200){
          for(yy <- 0 until 200){
            imageData.data(getIdx(xx, yy))   = muhColor.x.toInt
            imageData.data(getIdx(xx, yy)+1) = muhColor.y.toInt
            imageData.data(getIdx(xx, yy)+2) = muhColor.z.toInt
            imageData.data(getIdx(xx, yy)+3) = 255
          }
        }
      }
      imageData
    }
  }
}
