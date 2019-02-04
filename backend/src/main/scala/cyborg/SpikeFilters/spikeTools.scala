package cyborg

import cats.data.Kleisli
import fs2.{ Chunk, _ }
import cats._
import cats.effect._
import cats.implicits._
import utilz._

import scala.collection.mutable.{ Queue => MutableQueue }

class SpikeTools(kernelWidth: Int) {

  assert(kernelWidth%2 == 1, "Please supply an odd kernel width")

  // arbitrary
  val elementsPerChunk = 4*kernelWidth
  /**
    The gaussian blur convolutor not only outputs the convoluted value, it also outputs the raw input
    stream as a chunk aligned with the smoothed curve
    */
  def gaussianBlurConvolutor[F[_]]: Pipe[F,Int,(Chunk[Int], Chunk[Int])] = {

    val cutoff = (kernelWidth/2) + 1

    def go(s: Stream[F,Int], q: MutableQueue[Int], sum: Int): Pull[F, (Chunk[Int], Chunk[Int]), Unit] = {
      s.pull.unconsN(16*kernelWidth).flatMap {
        case Some((chunk, tl)) => {

          val convBuffer = Array.ofDim[Int](chunk.size - (cutoff))
          var sumRef = sum
          for(ii <- 0 until chunk.size - (cutoff)){
            convBuffer(ii) = sumRef
            sumRef = sumRef + chunk(ii + cutoff) - q.dequeue()
            q.enqueue(chunk(ii + cutoff))
          }
          Pull.output1((chunk.take(chunk.size - (cutoff)), Chunk.ints(convBuffer))) >>
            go(tl.cons(chunk.drop(chunk.size - (cutoff))), q, sumRef)
        }
        case None => Pull.done
      }
    }

    def init(s: Stream[F,Int]): Pull[F, (Chunk[Int], Chunk[Int]), Unit] = {
      s.pull.unconsN(kernelWidth, false) flatMap {
        case Some((chunk, tl)) => {
          val rest = chunk.drop(cutoff - 1)
          go(tl.cons(rest), MutableQueue[Int]() ++= chunk.toArray, chunk.foldMonoid)
        }
        case None => Pull.done
      }
    }

    inStream => init(inStream).stream
  }


  def avgNormalizer[F[_]]: Pipe[F,(Chunk[Int], Chunk[Int]), Int] = {

    def go(s: Stream[F,(Chunk[Int],Chunk[Int])]): Pull[F,Int,Unit] = {
      s.pull.uncons1.flatMap {
        case Some(((raw, convoluted), tl)) => {
          val buf = Array.ofDim[Int](raw.size)
          for(ii <- 0 until raw.size){
            buf(ii) = convoluted(ii) - raw(ii)
          }
          Pull.output(Chunk.ints(buf))
        }
        case None => Pull.done
      }
    }

    inStream => go(inStream).stream
  }
}
