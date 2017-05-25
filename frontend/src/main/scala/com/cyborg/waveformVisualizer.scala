package com.cyborg

import fs2._

import org.scalajs.dom
import org.scalajs.dom.html

object waveformVisualizer {

  class WFVisualizerControl(canvas: html.Canvas) {

    // hardcoded
    val vizHeight = 60
    val vizLength = 200
    val pointsPerSec = 40000
    val scalingFactor = 2000

    canvas.width = vizLength*8
    canvas.height = vizHeight*8

    val renderer = canvas.getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]

    renderer.font = "16 comic sans"
    renderer.textAlign = "center"
    renderer.textBaseline = "middle"

    type Color = Vec
    val Color = Vec
    case class Vec(x: Double, y: Double, z: Double)

    def gogo[F[_]](channelStreams: List[Stream[F,Int]]): Stream[F,Unit] = {
      impl.drawChannelData(channelStreams)
    }

    object impl {

      def drawChannelData[F[_]](channelStreams: List[Stream[F,Int]]): Stream[F,Unit] = {

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
        def drawMe(): Unit = renderer.putImageData(imageData, x.toDouble, y.toDouble)

        def go(previous: Vector[Int]): Handle[F,Int] => Pull[F,Int,Unit] = h => {
          h.await flatMap {
            case (chunk, h) => {
              val len = chunk.size
              val next = chunk.toVector ++ previous.dropRight(len)

              // TODO Unfuckulate this
              println("drawSink is drawing now")
              Pull.output1({draw(next, imageData); drawMe; 1}) >> go(next)(h)
            }
          }
        }
        _.pull(go(Vector.fill(200)(0))).drain
      }


      def draw(points: Vector[Int], imageData: dom.raw.ImageData): dom.raw.ImageData = {

        def makeRow(data: Int) = Vector.fill(data)(1) ++ Vector.fill(200-data)(0)
        def makeImg(data: Vector[Int]) = data.map(makeRow(_))

        def getIdx(x: Int, y: Int): Int =
          ((y*200) + x)*4

        val muhColor: Color = Vec(100.0, 100.0, 100.0)

        def drawPoint(x: Int, y: Int): Unit = {
          imageData.data(getIdx(x, y))   = muhColor.x.toInt
          imageData.data(getIdx(x, y)+1) = muhColor.y.toInt
          imageData.data(getIdx(x, y)+2) = muhColor.z.toInt
          imageData.data(getIdx(x, y)+3) = 255
        }

        def drawBlank(x: Int, y: Int): Unit = {
          imageData.data(getIdx(x, y))   = 0
          imageData.data(getIdx(x, y)+1) = 0
          imageData.data(getIdx(x, y)+2) = 0
          imageData.data(getIdx(x, y)+3) = 255
        }

        def plot(data: List[Vector[Int]]): Unit = {
          println("Drawing now")
          for(xx <- 0 until 200){
            for(yy <- 0 until 200){
              if(data(xx)(yy) == 1)
                drawPoint(xx, yy)
              else
                drawBlank(xx, yy)
            }
          }
        }
        imageData
      }
    }
  }
}
