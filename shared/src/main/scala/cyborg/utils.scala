package cyborg

import fs2._
import fs2.async.mutable.{ Queue, Topic }
import cats.effect.Effect
import scala.concurrent.ExecutionContext

import scala.concurrent.duration._


import scala.language.higherKinds

object utilz {

  type DataSegment = (Vector[Int], Int)
  type DataTopic[F[_]] = Topic[F,DataSegment]
  type MeameDataTopic[F[_]] = List[Topic[F,DataSegment]]
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
    in => go(in.buffer(4096)).stream
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
    */
  def mapN[F[_],I,O](length: Int, f: Segment[I,Unit] => O): Pipe[F,I,O] = {
    def go(s: Stream[F,I]): Pull[F,O,Unit] = {
      s.pull.unconsN(length.toLong, false).flatMap {
        case Some((seg, tl)) => {
          Pull.output1(f(seg)) >> go(tl)
        }
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


  /**
    Creates a list containing num topics of type T
    Requires an initial message init.
    */
  def createTopics[F[_]: Effect,T](num: Int, init: T)(implicit ec: ExecutionContext): Stream[F,List[Topic[F,T]]] = {
    val topicTask: F[Topic[F,T]] = fs2.async.topic[F,T](init)

    // TODO
    // should be until, not to?? Guess it doesn't matter since we just take, could just be
    // an iter -> repeatEval?

    // TODO wrong number of params??
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
    val throttleQueueTask = fs2.async.boundedQueue[F,Unit](10)

    // Should now periodically input tokens to the throttle queue
    val throttleStream: Stream[F, Unit] =
      Stream.eval(throttleQueueTask) flatMap { queue =>
        println(Console.RED + "I DONT WORK" + Console.RESET)
        val in = throttler.through(_.map{λ => println(λ);λ}).through(queue.enqueue)
        val out = queue.dequeue
        val spam = spamQueueSize("throttle q", queue, 1.second)
        in.concurrently(out.concurrently(spam))
      }

    s => s.zipWith(throttleStream)((λ,µ) => λ)
  }

  def throttul2[F[_]](period: FiniteDuration)(implicit s: Effect[F], t: Scheduler, ec: ExecutionContext): Stream[F,Unit] = {
    val throttler: Stream[F,Unit] = t.fixedRate(period)
    val throttleQueueTask = fs2.async.boundedQueue[F,Unit](10)

    val throttleStream: Stream[F, Unit] =
      Stream.eval(throttleQueueTask) flatMap { queue =>
        println(Console.RED + "I DONT WORK" + Console.RESET)
        val in = throttler.through(queue.enqueue)
        val out = queue.dequeue
        val spam = spamQueueSize("throttle q", queue, 1.second)
        in.concurrently(out.concurrently(spam))
      }
    throttleStream
  }

  def throttul[F[_]](period: FiniteDuration)(implicit s: Effect[F], t: Scheduler, ec: ExecutionContext): Stream[F,Unit] = {
    t.fixedRate(period)
  }


  /**
    Takes a stream of lists of streams and converts it into a single stream
    by selecting output from each input stream in a round robin fashion.

    Not built for speed, at least not when chunksize is low

    TODO: By adding queues we would stop blocking while waiting
    TODO: Verify that this is a perf problem
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
        case None => {
          println("log every got None")
          Pull.done
        }
      }
    }
    in => go(in).stream
  }

  def spamQueueSize[F[_]: Effect, I](name: String, q: Queue[F,I], dur: FiniteDuration)(implicit t: Scheduler, ec: ExecutionContext): Stream[F,Unit] = {
    throttul(dur).flatMap { _ =>
      Stream.eval(q.size.get).through(_.map(λ => println(s"Queue with name $name has a size of $λ")))
    }.repeat
  }

  def sineWave[F[_]](channels: Int, segmentLength: Int): Stream[F,Int] = {
    val empty: List[Int] = Nil
    val hurr: Stream[F,List[Int]] = Stream.iterate(0)(_+1).map{ iter =>
      val channel = iter % channels
      val offset = iter*segmentLength/channels
      // val α = ((channel + 1) % 6).toFloat/100.0
      val naughtyList = (0 until segmentLength).map(_ + offset).map{ idx => // get it?
        val sinVal = math.sin(idx*((channel % 6) + 1).toFloat/100.0)
        sinVal*400.0
      }
      val out = naughtyList.map(_.toInt).toList
      if(iter % 60 == 0){
        println(out.zipWithIndex.filter(_._2 % 5 == 0).map(_._1))
      }
      out
    }
    hurr.through(chunkify)
  }
}

