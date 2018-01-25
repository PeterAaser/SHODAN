package cyborg

import fs2._
import fs2.async.mutable.{ Queue, Topic }
import cats.effect.Effect
import scala.concurrent.ExecutionContext

import scala.concurrent.duration._

import scala.language.higherKinds

object utilz {

  type EC = ExecutionContext
  type Channel = Int
  case class TaggedSegment(data: (Channel, Vector[Int])) extends AnyVal
  type ChannelTopic[F[_]] = Topic[F,TaggedSegment]

  sealed trait Stoppable[F[_]] { def interrupt: F[_] }
  case class InterruptableAction[F[_]](interrupt: F[Unit], action: F[Unit]) extends Stoppable[F]

  def swapMap[A,B](m: Map[A,B]): Map[B,List[A]] =
    m.toList.groupBy(_._2).mapValues(_.map(_._1))


  def intersectWith[A, B, C, D](m1: Map[A, B], m2: Map[A, C])(f: (B, C) => D): Map[A, D] =
    for {
      (a, b) <- m1
      c <- m2.get(a)
    } yield a -> f(b, c)

  def intersect[A, B, C, D](m1: Map[A, B], m2: Map[A, C]): Map[A, (B,C)] =
    for {
      (a, b) <- m1
      c <- m2.get(a)
    } yield a -> (b, c)

  /**
    Decodes a byte stream from MEAME into a stream of 32 bit signed ints
    This pipe should only be used to deserialize data from MEAME
    */
  def bytesToInts[F[_]]: Pipe[F, Byte, Int] = {

    def go(s: Stream[F, Byte]): Pull[F,Int,Unit] = {
      s.pull.unconsN(4096, false)flatMap {
        case Some((seg, tl)) => {
          val chunk = seg.toChunk
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
              ((0xFF & y) << 8)  |
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


  /**
    Not really something that has any right to remain in the codebase.
    Sadly it's so ingrained now I cba removing it. Feel free to do so
    yourself!
    */
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
      s.pull.unconsN(stepsize.toLong, false).flatMap {
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
    Shouldn't really remain as a function when reduced to a call from the scheduler.
    */
  def tickSource[F[_]](period: FiniteDuration)(implicit s: Effect[F], t: Scheduler, ec: ExecutionContext): Stream[F,Unit] = {
    t.fixedRate(period)
  }


  /**
    Does not account for rounding errors!

    */
  def throttlerPipe[F[_]: Effect,I](elementsPerSec: Int, resolution: FiniteDuration)(implicit s: Scheduler, ec: EC): Pipe[F,I,I] = {

    val ticksPerSecond = (1.second/resolution)
    val elementsPerTick = (elementsPerSec/ticksPerSecond).toInt

    _.through(vectorize(elementsPerTick))
      .zip(tickSource(resolution))
      .through(_.map(_._1))
      .through(chunkify)
  }


  /**
    Takes a stream of lists of streams and converts it into a single stream
    by selecting output from each input stream in a round robin fashion.

    Not built for speed, at least not when chunksize is low
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
  def synchronize[F[_]]: Pipe[F,List[Stream[F,TaggedSegment]], List[Stream[F,Vector[Int]]]] = {

    /**
      Takes the first element of each inner stream and checks the tag.
      Drops the discrepancy between tags from each stream
      */
    def synchStreams(streams: Stream[F,List[Stream[F,TaggedSegment]]]) = {
      val a: Stream[F, Seq[TaggedSegment]] = roundRobin(streams)
      val b = a flatMap (λ =>
        {
          val tags = λ.map(_.data._1)
          val largest = tags.max
          val diffs = tags.map(largest - _)
          streams.map(λ => λ.zip(diffs).map(λ => λ._1.drop(λ._2.toLong).map(λ => λ.data._2)))
        })
      b
    }
    println("synchronize called. Might behave weirdly, not tested (lol)")
    synchStreams
  }


  def logEveryNth[F[_],I](n: Int): Pipe[F,I,I] = {
    def go(s: Stream[F,I]): Pull[F,I,Unit] = {
      s.pull.unconsN(n.toLong,false) flatMap {
        case Some((seg, tl)) => {
          println(seg.toList.head)
          Pull.output(seg) >> go(tl)
        }
        case None => {
          println("log every nth got None")
          Pull.done
        }
      }
    }
    in => go(in).stream
  }


  def logEveryNth[F[_],I](n: Int, say: I => Unit ): Pipe[F,I,I] = {
    def go(s: Stream[F,I]): Pull[F,I,Unit] = {
      s.pull.unconsN(n.toLong,false) flatMap {
        case Some((seg, tl)) => {
          say(seg.toList.head)
          Pull.output(seg) >> go(tl)
        }
        case None => {
          println("log every nth got None")
          Pull.done
        }
      }
    }
    in => go(in).stream
  }


  /**
    Prints the size of a queue to std.out at supplied interval.
    Used for unclogging
    */
  def spamQueueSize[F[_]: Effect, I](
    name: String,
    q: Queue[F,I],
    dur: FiniteDuration)(implicit t: Scheduler, ec: ExecutionContext): Stream[F,Unit] = {

    tickSource(dur).flatMap { _ =>
      Stream.eval(q.size.get).through(_.map(λ => println(s"Queue with name $name has a size of $λ")))
    }.repeat
  }


  // TODO: maybe rewrite with more idiomatic ops?
  def tagPipe[F[_]](segmentLength: Int): Pipe[F, Int, TaggedSegment] = {
    def go(n: Channel, s: Stream[F,Int]): Pull[F,TaggedSegment,Unit] = {
      s.pull.unconsN(segmentLength.toLong, false) flatMap {
        case Some((seg, tl)) => {
          Pull.output1(TaggedSegment(n, seg.toVector)) >> go((n + 1) % 60, tl)
        }
        case None => Pull.done
      }
    }
    in => go(0, in).stream
  }


  /**
    Delays execution of an action until the first element is pulled through the sink.
  */
  def evalOnFirstElement[F[_],A](action: F[Unit], s: Sink[F,A]): Sink[F,A] = stream =>
  stream.pull.uncons1.flatMap {
    case None => Pull.done
    case Some((hd, tl)) => {
      Pull.eval(action) >> Pull.output1(hd) >> tl.pull.echo
    }
  }.stream.to(s)


  /**
    Creates a sink that runs an action to create a sink when the first element arrives
    Thanks wedens!
    */
  def createOnFirstElement[F[_],A,B](action: F[A], sf: A => Sink[F,B]): Sink[F,B] = stream =>
  stream.pull.uncons1.flatMap {
    case None => Pull.done
    case Some((hd, tl)) => {
      Pull.eval(action).flatMap{ a =>
        Pull.output1((a, Stream.emit(hd) ++ tl))
      }
    }
  }.stream.flatMap { case (a, s) => s.to(sf(a)) }


}
