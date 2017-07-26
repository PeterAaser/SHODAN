package com.cyborg

import fs2._
import fs2.Stream._
import fs2.async.mutable.Topic
import fs2.util.Async
import fs2.async.mutable.Queue


import scala.language.higherKinds

object utilz {

  /**
    *   Outputs a list of streams that divides stream input in a round
    *   robin fashion. Each output gets size elements before the next
    *   output stream gets the handle.
    */
  def alternator[F[_]: Async, A](src: Stream[F,A], segmentLength: Int, outputs: Int, maxQueued: Int, log: (Unit => Unit) = _ => ())
      : Stream[F, Vector[Stream[F, A]]] = {

    // Fills a queue and then calls itself recursively on the next queue (mod outputs)
    def loop(activeQ: Int, qs: Vector[Queue[F,Vector[A]]]): Handle[F,A] => Pull[F,A,Unit] = h => {
      h.awaitN(segmentLength, false) flatMap {
        case (chunks, h) => {
          log(())
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
    val queueTasks = List.fill(outputs)(async.boundedQueue[F,Vector[A]](maxQueued))
    launch(queueAccumulator, queueTasks)
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


  /**
    Attaches an observer to a stream, used for separating out channels
    from the MEA
    */
  def attachChannelObservers[F[_]:Async](
    inStreams: Stream[F, Vector[Stream[F,Int]]],
    sinks: List[Sink[F,Int]],
    channels: List[Int]) = {

    val observeTask = inStreams.map( (streams: Vector[Stream[F,Int]]) =>
      {
        val selectedChannels: List[Stream[F,Int]] = channels.map(index => streams(index))
        val taskList = (selectedChannels zip sinks).map(utilz.mergeStreamSinkObserver(_).drain)
        val task = taskList.foldLeft(Stream[F,Unit]())(_++_).run
        task
    })
    observeTask

  }


  def mergeStreamSinkObserver[F[_]: Async,I]( channelSinkTuple: (Stream[F,I], Sink[F,I])): Stream[F,I] = {
    val channel = channelSinkTuple._1
    val sink = channelSinkTuple._2
    pipe.observe(channel)(sink)
  }


  /**
    Decodes a byte stream from MEAME into a stream of 32 bit signed ints
    This pipe should only be used to deserialize data from MEAME
    */
  def bytesToInts[F[_]]: Pipe[F, Byte, Int] = {

    def go: Handle[F,Byte] => Pull[F,Int,Unit] = h => {
      h.receive {
        case (chunk, h) => {
          if(chunk.size % 4 != 0){
            println("CHUNK MISALIGNMENT IN BYTES TO INTS CONVERTER")
            assert(false)
          }
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

          Pull.output(Chunk.seq(intBuf)) >> go(h)
        }
      }
    }
    _.pull(go)
  }


  /**
    Does what it says on the tin...
    not really perf critical
    */
  def doubleToByte[F[_]](silent: Boolean): Pipe[F, Double, Byte] = {

    import java.nio.ByteBuffer
    def doubleToByteArray(x: Double): Array[Byte] = {
      val l = java.lang.Double.doubleToLongBits(x)
      val bb = ByteBuffer.allocate(8).putLong(l).array().reverse
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


  /**
    Partitions a stream vectors of length n
    */
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


  // TODO figure out if this is actually needed or not
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


  /**
    Drops `factor` elements per emitted element
    */
  def downSample[F[_],I](factor: Int): Pipe[F,I,I] = {
    def go: Handle[F,I] => Pull[F,I,Unit] = h => {
      h.awaitN(factor, false).flatMap {
        case (chunks, h) => {
          Pull.output1(chunks.head.head) >> go(h)
        }
      }
    }
    _.pull(go)
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


  def movingAverage[F[_]](windowSize: Int): Pipe[F,Int,Int] = s =>
  s.through(pipe.sliding(windowSize)).through(_.map(_.foldLeft(0)(_+_)))


  /**
    A faster moving average, utilizing the fact that only the first and last element of the focus
    neighbourhood decides the value of the focus
    */
  def fastMovingAverage[F[_]](windowWidth: Int): Pipe[F,Int,Double] = {

    def go(window: Vector[Int]): Handle[F,Int] => Pull[F,Double,Unit] = h => {
      h.receive {
        (chunk, handle) => {
          val stacked = ((window ++ chunk.toVector) zip (chunk.toVector)).map(λ => (λ._1 - λ._2))
          val scanned = stacked.scanLeft(window.sum)(_+_).map(_.toDouble/windowWidth.toDouble)
          Pull.output(Chunk.seq(scanned)) >> go(chunk.toVector.takeRight(windowWidth))(h)
        }
      }
    }

    _ pull { h => h.awaitN(windowWidth).flatMap { case (chunks, h) =>
              val window = chunks.flatMap(_.toVector).toVector
              Pull.output1(window.sum.toDouble/windowWidth.toDouble) >> go(window)(h)
            }
    }
  }


  def printPipe[F[_],I](message: String): Pipe[F,I,I] =
    s => s.map( λ => { println(s"$message: $λ"); λ } )

  def printWithFormat[F[_],I](printer: I => String): Pipe[F,I,I] =
    s => s.map( λ => { println(printer(λ)); λ } )



  // TODO Not generalized
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

  type dataSegment = (Vector[Int], Int)
  type dataTopic = Topic[Task,dataSegment]

  // Should take in a datasource and emit a list of topics, one per channel
  def newIOmonstrosity(source: Stream[Task,Int])(implicit s: Strategy): Stream[Task,List[Topic[Task,dataSegment]]] = {
    import params.experiment._

    val topicTask: Task[dataTopic] = fs2.async.topic[Task,dataSegment]((Vector.empty,0))

    // creates a stream with a list of topics of dataSegment
    val topicStream = (Stream[Task,List[dataTopic]]() /: (0 to 60)){
      (acc: Stream[Task,List[dataTopic]], _) => {
        for {
          a <- acc
          b <- Stream.eval(topicTask)
        } yield b :: a
      }
    }

    // Ideally should take a bunch of topics and an input source and blast that shit loud yo
    def publishPipe(topics: List[dataTopic]): Sink[Task,Int] = h => {
      def loop(segmentId: Int, topics: List[dataTopic]): Handle[Task,Int] => Pull[Task,Task[Unit],Unit] = h => {
        h.awaitN(segmentLength*totalChannels, false) flatMap {
          case (chunks, h) => {
            val flattened = Chunk.concat(chunks).toVector
            val grouped = flattened.grouped(segmentLength)
            val stamped = grouped.map((_,segmentId)).toList
            val broadCasts = stamped.zip(topics).map{
              case(segment, topic) => topic.publish1(segment)
            }

            // TODO when fs2 cats integration hits traverse broadcasts and use Pull.eval
            Pull.output(Chunk.seq(broadCasts)) >> loop(segmentId + 1, topics)(h)
          }
        }
      }
      concurrent.join(totalChannels)(h.pull(loop(0, topics)).map(Stream.eval))
    }

    // Should ideally emit a list of topics doing their thing
    topicStream flatMap { topics =>
      source.through(publishPipe(topics)).mergeDrainL(Stream.emit(topics))
    }
  }
}
