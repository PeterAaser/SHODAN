package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.async.mutable.Queue

import java.nio.channels.AsynchronousChannelGroup
import java.lang.Thread.UncaughtExceptionHandler
import java.nio.channels.spi.AsynchronousChannelProvider
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import scodec._
import scodec.bits._
import codecs._
import scodec.stream._

import Attempt._
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
  def alternator[F[_]: Async, A](src: Stream[F,A], segmentLength: Int, outputs: Int)
      : Stream[F, Vector[Stream[F, A]]] = {

    // Fills a queue and then calls itself recursively on the next queue (mod outputs)
    def loop(activeQ: Int, qs: Vector[Queue[F,Vector[A]]]): Handle[F,A] => Pull[F,A,Unit] = h => {
      h.awaitN(segmentLength, false) flatMap {
        case (chunks, h) => {
          Pull.eval(qs(activeQ).enqueue1(chunks.flatMap(_.toVector).toVector)) >>
            loop(((activeQ + 1) % outputs), qs)(h)
        }
      }
    }

    // Recursively evaluates a list of queues and calls go on the evaluated queues
    def launch(
      accumulatedQueues: List[Queue[F,Vector[A]]],
      queueTasks: List[F[Queue[F,Vector[A]]]]): Stream[F,Vector[Stream[F,A]]] = {

      queueTasks match {
        case Nil => go(accumulatedQueues.toVector)
        case h :: t => Stream.eval(h).flatMap
          { qn: Queue[F,Vector[A]] => launch((qn :: accumulatedQueues), t) }
      }
    }

    def go(qs: Vector[Queue[F,Vector[A]]]): Stream[F, Vector[Stream[F, A]]] =
      src.pull(loop(0, qs)).drain merge Stream.emit((qs.map(_.dequeue.through(utilz.chunkify))))


    val queueAccumulator = List[Queue[F,Vector[A]]]()
    val queueTasks = List.fill(outputs)(async.boundedQueue[F,Vector[A]](1024))
    launch(queueAccumulator, queueTasks)
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
              // println(s"strided slide fillin up with $chunks")
              val window = chunks.foldLeft(Vector.empty[I])(_ ++ _.toVector)
              Pull.output1(window) >> go(window.drop(stepsize))(h)
            }}
  }


  def intsToBytes[F[_]]: Pipe[F, Int, Byte] = {

    def intToByteList(i: Int): List[Byte] =
      java.nio.ByteBuffer.allocate(4).putInt(i).array().toList

    def go: Handle[F, Int] => Pull[F, Byte, Unit] = h => {
      h.receive {
        case (chunk, h) => {
          val fug = chunk.toList.flatMap(intToByteList(_))
          val fugs = Chunk.seq(fug)
          Pull.output(Chunk.seq(fug)) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  // Decodes a byte stream from MEAME into a stream of 32 bit signed ints
  def bytesToInts[F[_]]: Pipe[F, Byte, Int] = {

    def go: Handle[F,Byte] => Pull[F,Int,Unit] = h => {
      h.receive {
        case (chunk, h) => {
          if(chunk.size % 4 != 0)
            assert(false)
          val intBuf = Array.ofDim[Int](chunk.size/4)

          for(i <- 0 until chunk.size/4){
            val idx = i*4
            val x = chunk(idx + 0)
            val y = chunk(idx + 1)
            val z = chunk(idx + 2)
            val w = chunk(idx + 3)

            val asInt: Int = (
              ((0xFF & w) << 24) |
                ((0xFF & z) << 16) |
                ((0xFF & y) << 8) |
                ((0xFF & x))
            )

            intBuf(i) = asInt
          }

          for(i <- 0 until chunk.size/4){
            println(intBuf(i))
          }

          Pull.output(Chunk.seq(intBuf)) >> go(h)
        }
      }
    }
    _.pull(go)
  }


  // TODO check if this is ok for performance (probably is)
  def byteToByteVec[F[_]]: Pipe[F,Byte,ByteVector] = {
    def go: Handle[F,Byte] => Pull[F,ByteVector,Unit] = h => {
      h.receive {
        case (chunk, h) => {
          Pull.output1(ByteVector(chunk.toArray)) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  // TODO check if this is ok for performance (probably is)
  def byteToBitVec[F[_]]: Pipe[F,Byte,BitVector] = {
    def go: Handle[F,Byte] => Pull[F,BitVector,Unit] = h => {
      h.receive {
        case (chunk, h) => {
          Pull.output1(BitVector(chunk.toArray)) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  // Decodes a double stream to a byte stream, not really perf critical
  def doubleToByte[F[_]](silent: Boolean): Pipe[F, Double, Byte] = {

    import java.nio.ByteBuffer
    def doubleToByteArray(x: Double): Array[Byte] = {
      val l = java.lang.Double.doubleToLongBits(x)
      val bb = ByteBuffer.allocate(8).putLong(l).array().reverse
      // if(!silent){
      // println(s"$x converted to byte array: ")
      // println(s"${bb(0)} ${bb(1)} ${bb(2)} ${bb(3)} ${bb(4)} ${bb(5)} ${bb(6)} ${bb(7)}")
      // }
      bb
    }

    def go: Handle[F,Double] => Pull[F,Byte,Unit] = h => {
      h.receive1 {
        (d, h) => {
          val le_bytes = doubleToByteArray(d)
          if(!silent)
            println(s"doubles 2 bytes seent $d which was packed to $le_bytes")
          Pull.output(Chunk.seq(le_bytes)) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  def streamLogger[A](prefix: String): Pipe[Task,A,A] = {
    _.evalMap { a => Task.delay{ println(s"$prefix> $a"); a }}
  }


  // TODO this thing has no reason to live other than being a bandaid.
  def unpacker[F[_],I]: Pipe[F,Seq[I],I] = {
    def go: Handle[F,Seq[I]] => Pull[F,I,Unit] = h => {
      h.receive1 {
        (v, h) => {
          Pull.output(Chunk.seq(v)) >> go(h)
        }
      }
    }
    _.pull(go)
  }


  def vectorize[F[_],I](length: Int): Pipe[F,I,Vector[I]] = {
    def go: Handle[F, I] => Pull[F,Vector[I],Unit] = h => {
      h.awaitN(length, false).flatMap {
        case (chunks, h) => {
          val folded = chunks.foldLeft(Vector.empty[I])(_ ++ _.toVector)
          Pull.output1(folded) >> go(h)
        }
      }
    }
    _.pull(go)
  }

  // Very likely not needed
  def chunkify[F[_],I]: Pipe[F, Seq[I], I] = {

    def go: Handle[F,Seq[I]] => Pull[F,I,Unit] = h => {
      h.receive1 {
        (v, h) => {
          // println(s"unchunked $v")
          Pull.output(Chunk.seq(v)) >> go(h)
        }
      }
    }

    _.pull(go)
  }


  def observerPipe[F[_]: Async](observer: Sink[F,Byte]):
      Pipe[F,(List[Double], List[Double]),(List[Double], List[Double])] = { s =>

    pipe.observeAsync(s, 256)(
      _.through(_.map(_._2))
        .through(utilz.chunkify)
        .through(utilz.doubleToByte(false))
        .through(observer))
  }

  def simpleJsonAssembler(electrodes: List[Int], stimFrequencise: List[Double]): String = {
    val electrodeString = electrodes.mkString("[", ", ", "]")
    val stimString = stimFrequencise.mkString("[", ", ", "]")

    // Apparently scala cannot escape quotes in string interpolation.
    // This will eventually be assembled with a real JSON handler. TODO
    val meme = "{ \"electrodes\" : " + s"${electrodeString}, " + "\"stimFreqs\" : " + s"${stimString} }\n"
    // println(memeString)
    meme
  }
}
