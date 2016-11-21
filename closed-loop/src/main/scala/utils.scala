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

import scala.language.higherKinds

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
      ( activeQ: Int, Qlist: Vector[Queue[F, Vector[A]]])
        : Handle[F, A] => Pull[F, Vector[A], Unit] = h => {

      h.awaitN(size, false).flatMap {
        case (chunks, h) => {
          Pull.eval(Qlist(activeQ).enqueue1(chunks.flatMap(_.toVector).toVector)) >>
            loop(((activeQ + 1) % outputs), Qlist)(h)
        }
      }
    }

    val makeQueue: F[Queue[F,Vector[A]]] = async.boundedQueue[F, Vector[A]](maxQueued)

    def makeQueues
      ( qs: List[Queue[F,Vector[A]]]
      , bs: List[F[Queue[F,Vector[A]]]]): Stream[F, List[Stream[F,Vector[A]]]] =

      bs match {
        case Nil => gogo(qs)
        case h :: t => Stream.eval(h).flatMap { qn => makeQueues((qn :: qs),t) }
      }

    def gogo(qs: List[Queue[F,Vector[A]]])
      = src.pull(loop(0, qs.toVector)).drain merge Stream.emit((qs.map(_.dequeue)))

    makeQueues(List[Queue[F,Vector[A]]](), (List.fill(outputs)(makeQueue)))
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
        // println(s"strided slide got $chunks")
        val window = chunks.foldLeft(last)(_ ++ _.toVector)
        Pull.output1(window) >> go(window.drop(stepsize))(h)
      }
    }

    _ pull { h => h.awaitN(windowWidth, false).flatMap { case (chunks, h) =>
              // println(s"strided slide fillin up with $chunks")
              val window = chunks.foldLeft(Vector.empty[I])(_ ++ _.toVector)
              Pull.output1(window) >> go(window.drop(stepsize))(h)
            }}
  }


  // Decodes a byte stream into a stream of 32 bit signed ints
  // TODO use scodec maybe?
  def bytesToInts[F[_]]: Pipe[F, Byte, Int] = {

    import java.nio.ByteBuffer

    def go: Handle[F,Byte] => Pull[F,Int,Unit] = h => {
      h.receive {
        case (chunk, h) => {
          if(chunk.size % 4 != 0)
            assert(false)
          val intBuf = Array.ofDim[Int](chunk.size/4)
          for(i <- 0 until chunk.size){
            intBuf(i / 4) = intBuf(i / 4) + (chunk(i) << (8*(i % 4)))
          }
          Pull.output(Chunk.seq(intBuf)) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  // Decodes a double stream to a byte stream, not really perf critical
  def doubleToByte[F[_]]: Pipe[F, Double, Byte] = {

    import java.nio.ByteBuffer
    def doubleToByteArray(x: Double) = {
      val l = java.lang.Double.doubleToLongBits(x)
      ByteBuffer.allocate(8).putLong(l).array()
    }

    def go: Handle[F,Double] => Pull[F,Byte,Unit] = h => {
      h.receive1 {
        (d, h) => {
          val le_bytes = doubleToByteArray(d)
          Pull.output(Chunk.seq(le_bytes)) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  def streamLogger[A](prefix: String): Pipe[Task,A,A] = {
    _.evalMap { a => Task.delay{ println(s"$prefix> $a"); a }}
  }

  def unpacker[F[_],I]: Pipe[F,Vector[I],I] = {
    def go: Handle[F,Vector[I]] => Pull[F,I,Unit] = h => {
      h.receive1 {
        (v, h) => {
          // println("unpacker received sumfin")
          // println(v)
          Pull.output(Chunk.seq(v)) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  def chunkify[F[_],I]: Pipe[F, Seq[I], I] = {

    def go: Handle[F,Seq[I]] => Pull[F,I,Unit] = h => {
      h.receive1 {
        (v, h) => {
          println(s"unchunked $v")
          Pull.output(Chunk.seq(v)) >> go(h)
        }
      }
    }

    _.pull(go)
  }

  def observerPipe[F[_]: Async](observer: Sink[F,Byte]): Pipe[F,List[Double],List[Double]] = { s =>

    pipe.observeAsync(s, 256)(_.through(utilz.chunkify).through(utilz.doubleToByte).through(observer))
  }
}

