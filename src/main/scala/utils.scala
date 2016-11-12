package SHODAN

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.async.mutable.Queue
import fs2.util.syntax._
import fs2.io.file._
import fs2.io.tcp._

import java.nio.file._
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import scala.concurrent.duration._
import java.lang.Thread.UncaughtExceptionHandler
import java.net.InetSocketAddress
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import scodec.codecs
import scodec.stream.{encode,decode,StreamDecoder,StreamEncoder}
import scodec.bits.ByteVector

object namedACG {

  def namedACG(name:String):AsynchronousChannelGroup = {
    val idx = new AtomicInteger(0)
    AsynchronousChannelProvider.provider().openAsynchronousChannelGroup(
      8
        , new ThreadFactory {
        def newThread(r: Runnable): Thread = {
          val t = new Thread(r, s"fs2-AG-$name-${idx.incrementAndGet() }")
          t.setDaemon(true)
          t.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
                                          def uncaughtException(t: Thread, e: Throwable): Unit = {
                                            println("----------- UNHANDLED EXCEPTION ---------")
                                            e.printStackTrace()
                                          }
                                        })
          t
        }
      }
    )
  }

}
object utilz {

  /**
    *   Outputs a list of streams that divides stream input in a round
    *   robin fashion. Each output gets size elements before the next
    *   output stream gets the handle.
    */

  def alternate[F[_]: Async, A]
    ( src: Stream[F, A]
    , size: Int
    , maxQueued: Int
    , outputs: Int

    ): Stream[F, List[Stream[F, Vector[A]]]] = {

    def loop
      ( activeQ: Int, Qlist: Vector[Queue[F, Vector[A]]]
      ): Handle[F, A] => Pull[F, Vector[A], Unit] = h => {

      h.awaitN(size, false).flatMap {
        case (chunks, h) => {
          Pull.eval(Qlist(activeQ).enqueue1(chunks.flatMap(_.toVector).toVector)) >>
            loop(((activeQ + 1) % outputs), Qlist)(h)
        }
      }
    }

    val mkQueue: F[Queue[F,Vector[A]]] = async.boundedQueue[F, Vector[A]](maxQueued)

    def mkQs
      ( qs: List[Queue[F,Vector[A]]]
      , bs: List[F[Queue[F,Vector[A]]]]): Stream[F, List[Stream[F,Vector[A]]]] =

      bs match {
        case Nil => gogo(qs)
        case h :: t => Stream.eval(h).flatMap { qn => mkQs((qn :: qs),t) }
      }

    def gogo(qs: List[Queue[F,Vector[A]]])
      = src.pull(loop(0, qs.toVector)).drain merge Stream.emit((qs.map(_.dequeue)))

    mkQs(List[Queue[F,Vector[A]]](), (List.fill(outputs)(mkQueue)))
  }


  /**
    * Groups inputs in fixed size chunks by passing a "sliding window"
    * of size `width` and with an overlap `stride` over them.
    *
    * @example {{{
    * scala> Stream(1, 2, 3, 4, 5, 6, 7).stridedSliding(3, 1).toList
    * res0: List[Vector[Int]] = List(Vector(1, 2, 3), Vector(3, 4, 5), Vector(5, 6, 7))
    * }}}
    * @throws scala.IllegalArgumentException if `n` <= 0
    */

  def stridedSlide[F[_],I](windowWidth: Int, overlap: Int): Pipe[F,I,Vector[I]] = {
    require(windowWidth > 0,       "windowWidth must be > 0")
    require(windowWidth > overlap, "windowWidth must be wider than overlap")
    val stepsize = windowWidth - overlap
    def go(last: Vector[I]): Handle[F,I] => Pull[F,Vector[I],Unit] = h => {
      h.awaitN(stepsize, false).flatMap { case (chunks, h) =>
        val window = chunks.foldLeft(last)(_ ++ _.toVector)
        Pull.output1(window) >> go(window.drop(stepsize))(h)
      }
    }

    _ pull { h => h.awaitN(windowWidth, false).flatMap { case (chunks, h) =>
              val window = chunks.foldLeft(Vector.empty[I])(_ ++ _.toVector)
              Pull.output1(window) >> go(window.drop(stepsize))(h)
            }}
  }


  // Decodes a byte stream into a stream of 32 bit signed ints
  // I sincerely doubt it works...
  // Nvm, it works (in the simplest case at least)
  // TODO use scodec maybe?
  def bytesToInts: Pipe[Task, Byte, Int] = {

    import java.nio.ByteBuffer

    def go: Handle[Task,Byte] => Pull[Task,Int,Unit] = h => {
      h.awaitN(256) flatMap {
        case (chunks, h) => {
          val bytes = (Vector[Byte]() /: chunks)(_++_.toVector)
          val intBuf = Array.ofDim[Int](64)
          for(i <- 0 until 256){
            intBuf(i / 4) = intBuf(i / 4) + (bytes(i) << (8*(i % 4)))
          }
          Pull.output(Chunk.seq(intBuf.toVector)) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  def doubleToByte: Pipe[Task, Double, Byte] = {

    import java.nio.ByteBuffer

    def doubleToByteArray(x: Double) = {
      val l = java.lang.Double.doubleToLongBits(x)
      ByteBuffer.allocate(8).putLong(l).array()
    }

    def go: Handle[Task,Double] => Pull[Task,Byte,Unit] = h => {
      h.receive1 {
        (d, h) => {
          Pull.output(Chunk.seq(doubleToByteArray(d))) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  def streamLogger[A](prefix: String): Pipe[Task,A,A] = {
    _.evalMap { a => Task.delay{ println(s"$prefix> $a"); a }}
  }

  def unpacker[I]: Pipe[Task,Vector[I],I] = {
    def go: Handle[Task,Vector[I]] => Pull[Task,I,Unit] = h => {
      h.receive1 {
        (v, h) => {
          Pull.output(Chunk.seq(v)) >> go(h)
        }
      }
    }
    _.pull(go)
  }
}
