package com.cyborg

import fs2._
import fs2.async.mutable.{ Queue, Topic }
import cats.effect.Effect
import cats.effect.IO
import scala.concurrent.ExecutionContext

import scala.concurrent.duration._


import scala.language.higherKinds

object utilz {

  type DataSegment = (Vector[Int], Int)
  type DataTopic[F[_]] = Topic[F,DataSegment]
  type MeameDataTopic[F[_]] = List[Topic[F,DataSegment]]
  type DbDataTopic[F[_]] = List[Topic[F,DataSegment]]
  type Channel = Int


  /**
    Decodes a byte stream from MEAME into a stream of 32 bit signed ints
    This pipe should only be used to deserialize data from MEAME
    */
  def bytesToInts[F[_]]: Pipe[F, Byte, Int] = {

    def go(s: Stream[F, Byte]): Pull[F,Int,Unit] = {
      s.pull.unconsChunk flatMap {
        case Some((chunk, tl)) => {
          if(chunk.size % 4 != 0){
            println( Console.RED + "CHUNK MISALIGNMENT IN BYTES TO INTS CONVERTER" + Console.RESET )
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

          Pull.output(Chunk.seq(intBuf)) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in).stream
  }


  /**
    Encodes int to byte arrays. Assumes 4 bit integers
    */
  def intToBytes[F[_]]: Pipe[F, Int, Array[Byte]] = {

    def go(s: Stream[F, Int]): Pull[F,Array[Byte],Unit] = {
      s.pull.uncons flatMap {
        case Some((seg, tl)) => {

          val data = seg.toArray
          val bb = java.nio.ByteBuffer.allocate(data.length*4)
          for(ii <- 0 until data.length){
            bb.putInt(data(ii))
          }
          Pull.output(Segment(bb.array())) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in).stream
  }



  /**
    Partitions a stream vectors of length n
    */
  def vectorize[F[_],I](length: Int): Pipe[F,I,Vector[I]] = {
    def go(s: Stream[F,I]): Pull[F,Vector[I],Unit] = {
      s.pull.unconsN(length.toLong, false).flatMap {
        case Some((segment, tl)) => {
          Pull.output1(segment.toVector) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in).stream
  }


  /**
    Partitions a stream vectors of length n
    TODO: Not sure if this should be here
    */
  def vectorizeList[F[_],I](length: Int): Pipe[F,I,List[I]] = {
    def go(s: Stream[F,I]): Pull[F,List[I],Unit] = {
      s.pull.unconsN(length.toLong, false).flatMap {
        case Some((segment, tl)) => {
          Pull.output1(segment.toList) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in).stream
  }


  // TODO figure out if this is actually needed or not
  // Very likely not needed
  def chunkify[F[_],I]: Pipe[F, Seq[I], I] = {

    def go(s: Stream[F,Seq[I]]): Pull[F,I,Unit] = {
      s.pull.uncons1 flatMap {
        case Some((segment, tl)) =>
          Pull.output(Segment.seq(segment)) >> go(tl)
        case None => Pull.done
      }
    }
    in => go(in).stream
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
    def go(s: Stream[F,I], last: Vector[I]): Pull[F,Vector[I],Unit] = {
      s.pull.unconsN(stepsize, false).flatMap {
        case Some((seg, tl)) =>
          Pull.output1(seg.toVector) >> go(tl, seg.toVector.drop(stepsize))
        case None => Pull.done
      }
    }

    in => in.pull.unconsN(windowWidth.toLong, false).flatMap {
      case Some((seg, tl)) => {
        Pull.output1(seg.toVector) >> go(tl, seg.toVector.drop(stepsize))
      }
      case None => Pull.done
    }.stream

  }


  /**
    A faster moving average, utilizing the fact that only the first and last element of the focus
    neighbourhood decides the value of the focus
    TODO: Just rename to moving average...
    */
  def fastMovingAverage[F[_]](windowWidth: Int): Pipe[F,Int,Double] = {

    def go(s: Stream[F,Int], window: Vector[Int]): Pull[F,Double,Unit] = {
      s.pull.uncons flatMap {
        case Some((seg, tl)) => {
          val stacked = ((window ++ seg.toVector) zip (seg.toVector)).map(λ => (λ._1 - λ._2))
          val scanned = stacked.scanLeft(window.sum)(_+_).map(_.toDouble/windowWidth.toDouble)
          Pull.output(Chunk.seq(scanned)) >> go(tl, seg.toVector.takeRight(windowWidth))
        }
        case None => Pull.done
      }
    }

    in => in.pull.unconsN(windowWidth.toLong).flatMap {
      case Some((seg, tl)) => {
        Pull.output1(seg.toList.sum.toDouble/windowWidth.toDouble) >> go(tl, seg.toVector)
      }
      case None => Pull.done
    }.stream
  }


  def printPipe[F[_],I](message: String): Pipe[F,I,I] =
    s => s.map( λ => { println(s"$message: $λ"); λ } )

  def printWithFormat[F[_],I](printer: I => String): Pipe[F,I,I] =
    s => s.map( λ => { println(printer(λ)); λ } )


  // TODO: delete
  def simpleJsonAssembler(electrodes: List[Int], stimFrequencise: List[Double]): String = {
    val electrodeString = electrodes.mkString("[", ", ", "]")
    val stimString = stimFrequencise.mkString("[", ", ", "]")

    // Apparently scala cannot escape quotes in string interpolation.
    // This will eventually be assembled with a real JSON handler. TODO
    val jsonString = "{ \"electrodes\" : " + s"${electrodeString}, " + "\"stimFreqs\" : " + s"${stimString} }\n"
    jsonString
  }


  /**
    Creates a list containing num topics of type T
    Requires an initial message init.
    */
  // TODO dbg
  // Testes i SAtest
  def createTopics[F[_]: Effect,T](num: Int, init: T)(implicit ec: ExecutionContext): Stream[F,List[Topic[F,T]]] = {
    val topicTask: F[Topic[F,T]] = fs2.async.topic[F,T](init)
    val topicStream: Stream[F,Topic[F,T]] = (Stream[Topic[F,T]]().covary[F].repeat /: (0 to num)){
      (acc: Stream[F,Topic[F,T]], _) => {
        Stream.eval(topicTask) ++ acc
      }
    }
    topicStream.through(utilz.vectorizeList(num))
  }


  /**
    Periodically emits "tokens" to a queue. By zipping the output of the queue with a
    stream and then discarding the token the stream will be throttled to the rate of token outputs.
    TODO: I think this is now a part of the standard fs2 lib
    */
  def throttle[F[_], I](period: FiniteDuration)(implicit s: Effect[F], t: Scheduler, ec: ExecutionContext): Pipe[F,I,I] = {

    val throttler: Stream[F,Unit] = t.fixedRate(period)
    val throttleQueueTask = fs2.async.circularBuffer[F,Unit](10)

    // Should now periodically input tokens to the throttle queue
    val throttleStream: Stream[F, Unit] =
      Stream.eval(throttleQueueTask) flatMap { queue =>
        val in = throttler.through(queue.enqueue)
        val out = queue.dequeue
        in.concurrently(out)
      }

    s => s.zip(throttleStream).map(_._1)
  }

  def throttul(period: FiniteDuration)(implicit s: Effect[IO], t: Scheduler, ec: ExecutionContext): Stream[IO,Unit] = {
    t.fixedRate(100.millis)
  }

  /**
    Takes a stream of lists of streams and converts it into a single stream
    by selecting output from each input stream in a round robin fashion.

    Not built for speed, at least not when chunksize is low

    TODO: By adding queues we would stop blocking while waiting
    */
  def roundRobin[F[_],I]: Pipe[F,List[Stream[F,I]],List[I]] = _.flatMap(roundRobinL)


  def roundRobinL[F[_],I](streams: List[Stream[F,I]]): Stream[F,List[I]] = {
    def zip2List(a: Stream[F,I], b: Stream[F,List[I]]): Stream[F,List[I]] = {
      a.zipWith(b)(_::_)
    }
    val mpty: Stream[F,List[I]] = Stream(List[I]()).repeat
    streams.foldLeft(mpty)((λ,µ) => zip2List(µ,λ))
  }


  /**
    Synchronizes a list of streams, discarding segment ID
    */
  def synchronize[F[_]]: Pipe[F,List[Stream[F,DataSegment]], List[Stream[F,Vector[Int]]]] = {

    /**
      Takes the first element of each inner stream and checks the tag.
      Drops the discrepancy between tags from each stream
      */
    def synchStreams(streams: Stream[F,List[Stream[F,DataSegment]]]) = {
      val a: Stream[F, Seq[DataSegment]] = roundRobin(streams)
      val b = a flatMap (λ =>
        {
          val tags = λ.map(_._2)
          val largest = tags.max
          val diffs = tags.map(largest - _)
          streams.map(λ => λ.zip(diffs).map(λ => λ._1.drop(λ._2.toLong).map(λ => λ._1)))
        })
      b
    }

    synchStreams
  }


  /**
    Normally a pipe like this would be implemented in a much simpler fashion.
    The reason for making a more low level implementation is that in many cases
    you downsample for performance reasons, so if you can effectively downsample then
    less optimized pipes downstream won't matter too much.
    */
  def downSamplePipe[F[_],I](blockSize: Int): Pipe[F,I,I] = {
    def go(s: Stream[F,I], cutoffPoint: Int): Pull[F,I,Unit] = {
      s.pull.unconsChunk flatMap {
        case Some((chunk, tl)) => {

          val sizeAfterCutoff = chunk.size - cutoffPoint
          val numSamples = (sizeAfterCutoff / blockSize) + (if ((sizeAfterCutoff % blockSize) == 0) 0 else 1)
          val indicesToSample = List.tabulate(numSamples)(λ => (λ*blockSize) + cutoffPoint)

          // Calculates how many elements to drop from next chunk
          val cutoffElements = (chunk.size - cutoffPoint) % blockSize
          val nextCutOffPoint = (blockSize - cutoffElements) % blockSize

          val samples = indicesToSample.map(chunk(_))


          Pull.output(Chunk.seq(samples)) >> go(tl, nextCutOffPoint)
        }
        case None => Pull.done
      }
    }
    in => go(in, 0).stream
  }

  def logEveryNth[F[_],I](n: Int): Pipe[F,I,I] = {
    def go(s: Stream[F,I]): Pull[F,I,Unit] = {
      s.pull.unconsN(n,false) flatMap {
        case Some((seg, tl)) => {
          println(seg.toList.head)
          Pull.output(seg) >> go(tl)
        }
      }
    }
    in => go(in).stream
  }

  def logEveryNth[F[_],I](n: Int, say: I => Unit ): Pipe[F,I,I] = {
    def go(s: Stream[F,I]): Pull[F,I,Unit] = {
      s.pull.unconsN(n,false) flatMap {
        case Some((seg, tl)) => {
          say(seg.toList.head)
          Pull.output(seg) >> go(tl)
        }
        case None => go(s)
      }
    }
    in => go(in).stream
  }

  def spamQueueSize[F[_]: Effect, I](name: String, q: Queue[F,I])(implicit t: Scheduler, ec: ExecutionContext): Stream[F,Unit]= {
    val meme = Stream.eval(q.size.get).repeat.through(logEveryNth(100000, λ => println(s"Queue with name $name has size $λ")))
    meme.drain
  }
}

