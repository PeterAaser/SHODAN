package cyborg

import cats.effect.{ Async, Sync }
import cats.{ Functor, Monad }
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.{ Queue, Signal, Topic }
import cats.effect.Effect
import java.nio.{ ByteBuffer, ByteOrder }
import scala.concurrent.ExecutionContext

import scala.concurrent.duration._

import scala.language.higherKinds
import scala.reflect.ClassTag
import sourcecode._

object utilz {

  type EC = ExecutionContext

  type Channel = Int
  case class TaggedSegment(channel: Channel, data: Vector[Int])
  type ChannelTopic[F[_]] = Topic[F,TaggedSegment]

  sealed trait Stoppable[F[_]] { def interrupt: F[_] }
  case class InterruptableAction[F[_]](interrupt: F[Unit], action: F[Unit]) extends Stoppable[F]
  object InterruptableAction {
    def apply[F[_]: Effect](a: Stream[F,Unit])(implicit ec: EC): F[InterruptableAction[F]] = {
      val interrupted = fs2.async.signalOf[F,Boolean](false)
      interrupted.map { interruptSignal =>
        InterruptableAction(
          interruptSignal.set(true),
          a.interruptWhen(interruptSignal.discrete).compile.drain
        )}
    }
  }

  def bytesToInts[F[_]](format: String = "Csharp"): Pipe[F, Byte, Int] = format match {
    case "Csharp" => bytesToIntsMEAME
    case "JVM" => bytesToIntsJVM
  }

  /**
    Decodes a byte stream from MEAME into a stream of 32 bit signed ints
    This pipe should only be used to deserialize data from MEAME
    */
  def bytesToIntsMEAME[F[_]]: Pipe[F, Byte, Int] = {

    def go(s: Stream[F, Byte]): Pull[F,Int,Unit] = {
      s.pull.unconsN(4096, false)flatMap {
        case Some((seg, tl)) => {
          val chunk = seg.force.toChunk
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

          Pull.output(Chunk.seq(intBuf).toSegment) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in.buffer(4096)).stream
  }


  def bytesToIntsJVM[F[_]]: Pipe[F, Byte, Int] = {

    def go(s: Stream[F, Byte]): Pull[F,Int,Unit] = {
      s.pull.unconsN(4096, false)flatMap {
        case Some((seg, tl)) => {
          val data = seg.force.toArray
          val intbuf = ByteBuffer.wrap(data)
            .order(ByteOrder.BIG_ENDIAN)
            .asIntBuffer()
          val outs = Array.ofDim[Int](1024)
          intbuf.get(outs)
          val huh = Segment.seq(outs)
          Pull.output(huh) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in.buffer(4096)).stream
  }


  def intToBytes[F[_]]: Pipe[F, Int, Byte] = {

    def go(s: Stream[F, Int]): Pull[F,Byte,Unit] = {
      s.pull.unconsN(1024, false)flatMap {
        case Some((seg, tl)) => {
          val data = seg.force.toArray
          val bb = ByteBuffer.allocate(4096)
          bb.asIntBuffer().put(data)
          val outs = bb.array()
          val huh = Segment.seq(outs)
          Pull.output(huh) >> go(tl)
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
          Pull.output1(segment.force.toVector) >> go(tl)
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
          Pull.output1(segment.force.toList) >> go(tl)
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
          val forced = seg.force.toVector
          Pull.output1(forced) >> go(tl, forced.drop(stepsize))
        case None => Pull.done
      }
    }

    in => in.pull.unconsN(windowWidth.toLong, false).flatMap {
      case Some((seg, tl)) => {
        val forced = seg.force.toVector
        Pull.output1(forced) >> go(tl, forced.drop(stepsize))
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
          val stacked = ((window ++ seg.force.toVector) zip (seg.force.toVector)).map(z => (z._1 - z._2))
          val scanned = stacked.scanLeft(window.sum)(_+_).map(_.toDouble/windowWidth.toDouble)
          Pull.output(Chunk.seq(scanned).toSegment) >> go(tl, seg.force.toVector.takeRight(windowWidth))
        }
        case None => Pull.done
      }
    }

    in => in.pull.unconsN(windowWidth.toLong).flatMap {
      case Some((seg, tl)) => {
        Pull.output1(seg.force.toList.sum.toDouble/windowWidth.toDouble) >> go(tl, seg.force.toVector)
      }
      case None => Pull.done
    }.stream
  }


  /**
    Creates a list containing num topics of type T
    Requires an initial message init.
    */
  def createTopics[F[_]: Effect,T](init: T)(implicit ec: ExecutionContext): Stream[F,Topic[F,T]] = {
    val topicTask: F[Topic[F,T]] = fs2.async.topic[F,T](init)
    Stream.repeatEval(topicTask)
  }


  /**
    Does not account for rounding errors!

    */
  def throttlerPipe[F[_]: Effect,I](elementsPerSec: Int, resolution: FiniteDuration)
    (implicit s: Scheduler, ec: EC): Pipe[F,I,I] = {
    val ticksPerSecond = (1.second/resolution)
    val elementsPerTick = (elementsPerSec/ticksPerSecond).toInt

    _.through(vectorize(elementsPerTick))
      .zip(s.fixedRate(resolution))
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
    streams.foldRight(mpty)((z,u) => zip2List(z,u))
  }

  def roundRobinQ[F[_]: Effect : Functor,I:ClassTag](streams: List[Stream[F,I]])(implicit ec: EC): Stream[F,List[I]] = {

    val numStreams = streams.size
    val elementsPerDeq = 20

    Stream.repeatEval(fs2.async.boundedQueue[F,Vector[I]](100)).through(vectorize(numStreams)) flatMap { qs =>
      Stream.eval(fs2.async.boundedQueue[F,List[I]](10)) flatMap { outQueue =>

        val inputTasks = (qs zip streams).map{ case(q, s) => s.through(vectorize(elementsPerDeq)).through(q.enqueue).compile.drain }
        val inputTask = Stream.emits(inputTasks).map(Stream.eval(_)).join(numStreams)

        val arr = Array.ofDim[I](elementsPerDeq, numStreams)

        def outputFillTask: F[Unit] = {

          def unloader(idx: Int): F[Unit] = {
            if(idx == numStreams)
              loader(0)
            else {
              outQueue.enqueue1(arr(idx).toList) >> unloader(idx + 1)
            }
          }

          def loader(idx: Int): F[Unit] = {
            if(idx == numStreams) {
              unloader(0)
            }
            else {
              (qs(idx).dequeue1 map { xs: Vector[I] =>
                 for(i <- 0 until elementsPerDeq){
                   arr(i)(idx) = xs(i)
                 }
               }) >> loader(idx + 1)
            }
          }
          loader(0)
        }

        outQueue.dequeue.concurrently(inputTask).concurrently(Stream.eval(outputFillTask))
      }
    }
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
      val b = a flatMap (z =>
        {
          val tags = z.map(_.channel)
          val largest = tags.max
          val diffs = tags.map(largest - _)
          streams.map(z => z.zip(diffs).map(z => z._1.drop(z._2.toLong).map(z => z.data)))
        })
      b
    }
    say("synchronize called. Might behave weirdly, not tested (lol)")
    synchStreams
  }


  def logEveryNth[F[_],I](n: Int): Pipe[F,I,I] = {
    def go(s: Stream[F,I]): Pull[F,I,Unit] = {
      s.pull.unconsN(n.toLong,false) flatMap {
        case Some((seg, tl)) => {
          say(s"${seg.force.toList.head}")
          Pull.output(seg) >> go(tl)
        }
        case None => {
          say("log every nth got None")
          Pull.done
        }
      }
    }
    in => go(in).stream
  }


  def logEveryNth[F[_],I](n: Int, message: I => Any): Pipe[F,I,I] = {
    def go(s: Stream[F,I]): Pull[F,I,Unit] = {
      s.pull.unconsN(n.toLong,false) flatMap {
        case Some((seg, tl)) => {
          message(seg.force.toList.head)
          Pull.output(seg) >> go(tl)
        }
        case None => {
          say("log every nth got None")
          Pull.done
        }
      }
    }
    in => go(in).stream
  }


  def logEveryNth[F[_],I](n: Int, message: String): Pipe[F,I,I] = {
    logEveryNth(n, z => say(message, timestamp = true))
  }


  /**
    Prints the size of a queue to std.out at supplied interval.
    Used for unclogging
    */
  def spamQueueSize[F[_]: Effect, I](
    name: String,
    q: Queue[F,I],
    dur: FiniteDuration)(implicit t: Scheduler, ec: ExecutionContext): Stream[F,Unit] = {

    t.fixedRate(dur).flatMap { _ =>
      Stream.eval(q.size.get).through(_.map(z => say(s"Queue with name $name has a size of $z")))
    }.repeat
  }


  def tagPipe[F[_]](segmentLength: Int): Pipe[F, Int, TaggedSegment] = {
    def go(n: Channel, s: Stream[F,Int]): Pull[F,TaggedSegment,Unit] = {
      s.pull.unconsN(segmentLength.toLong, false) flatMap {
        case Some((seg, tl)) => {
          Pull.output1(TaggedSegment(n, seg.force.toVector)) >> go((n + 1) % 60, tl)
        }
        case None => Pull.done
      }
    }
    in => go(0, in).stream
  }


  def testTagPipe[F[_]](segmentLength: Int): Pipe[F, Int, TaggedSegment] = {
    def go(n: Channel, s: Stream[F,Int]): Pull[F,TaggedSegment,Unit] = {
      s.pull.unconsN(segmentLength.toLong, false) flatMap {
        case Some((seg, tl)) => {
          Pull.output1(TaggedSegment(n, seg.force.toVector.map(_ => n*10))) >> go((n + 1) % 60, tl)
        }
        case None => Pull.done
      }
    }
    in => go(0, in).stream
  }


  /**
   * Outputs a single channel from a stream of interleaved
   * channels. The functionality is similar to that of tagPipe, the
   * difference being that singleChannelPipe demuxes and then outputs
   * only a single channel.
   */
  def singleChannelPipe[F[_]](channel: Channel, channels: Int, segmentLength: Int)
      : Pipe[F,Int,Int] = {
    def go(n: Channel, s: Stream[F,Int]): Pull[F,Int,Unit] = {
      s.pull.unconsN(segmentLength.toLong, false) flatMap {
        case None => Pull.done
        case Some((seg,tl)) => {
          n match {
            case n if n == channel => Pull.output(seg) >> go((n+1)%channels, tl)
            case _ => go((n+1)%channels, tl)
          }
        }
      }
    }

    in => go(0, in).stream
  }


  /**
   * A naive implementation of downsampling that does not include an
   * anti-aliasing filter. Loosely speaking this is just a
   * downsampling.
   */
  def naiveDecimationPipe[F[_]](factor: Int): Pipe[F,Int,Int] = {
    def go(s: Stream[F,Int]): Pull[F,Int,Unit] = {
      s.pull.unconsN(factor.toLong, false) flatMap {
        case None => Pull.done
        case Some((seg,tl)) => {
          Pull.output1(seg.force.toList.head) >> go(tl)
        }
      }
    }

    in => go(in).stream
  }


  /**
    * Sink for visualizing an integer stream with ReservoirPlot. The
    * plot is owned by the Sink itself, which will periodically expand
    * the dataset as the content of the Stream is pulled.
    */
  def vizSink[F[_]: Sync](samplerate: Int, resolution: FiniteDuration = 0.1.second)
      : Sink[F, Int] = {
    val plot = new ReservoirPlot.TimeSeriesPlot(
      Seq.fill(ReservoirPlot.getSlidingWindowSize(samplerate, resolution))(0.0f).toArray,
      samplerate, resolution
    )
    plot.show

    // Note that the vectorization is of size samplerate, to make sure
    // it's unlikely that Timer in ReservoirPlot will ever catch up.
    _.through(utilz.vectorize(samplerate)).through(
      Sink.apply(xs =>
        Sync[F].delay(
          {
            // Not thread safe (do we care?)
            plot ++= xs.map(_.toFloat).toArray
          }
        )
      )
    )
  }


  /**
    Modifies the segment length of a stream. Not optimized
    */
  def modifySegmentLengthGCD[F[_]](originalSegmentLength: Int, newSegmentLength: Int, channels: Int = 60): Pipe[F,Int,Int] = {

    def gcd(a: Int,b: Int): Int = {
      if(b ==0) a else gcd(b, a%b)
    }
    val divisor = gcd(originalSegmentLength, newSegmentLength)

    say(s"modify seg with original: $originalSegmentLength, new: $newSegmentLength, gcd: $divisor")

    def disassemble: Pipe[F,Int,List[Int]] = {

      val outCols = channels*divisor
      val outRows = (originalSegmentLength/divisor)
      val pullSize = channels*originalSegmentLength

      // say(s"outCols is $outCols")
      // say(s"outRows is $outRows")
      // say(s"pullSize is $pullSize")

      val outBuffers = Array.ofDim[Int](outRows, outCols)

      def go(s: Stream[F,Int]): Pull[F,List[Int],Unit] = {
        s.pull.unconsN(channels*originalSegmentLength.toLong, false).flatMap {
          case Some((seg,tl)) => {
            // say("disassembling")
            val data = seg.force.toArray
            for(i <- 0 until pullSize){
              val outRow = (i/divisor)%outRows
              // val outRow = (i/outCols)
              val outCol = (i%divisor) + divisor*(i/originalSegmentLength)
              // say(s"with i: $i we get outBuf[$outRow][$outCol] <- ${data(i)}")
              outBuffers(outRow)(outCol) = data(i)
            }
            // say(s"disassembler outputting ${outBuffers.map(_.toList).toList} \n")
            Pull.outputChunk(Chunk.seq(outBuffers.map(_.toList).toList)) >> go(tl)
          }
          case None => Pull.done
        }
      }

      in => go(in).stream
    }

    def reassemble: Pipe[F,List[Int], Int] = {

      val pullSize = (newSegmentLength/divisor)
      val outBuf: Array[Int] = new Array(newSegmentLength*channels)

      def go(s: Stream[F,List[Int]]): Pull[F,Int,Unit] = {
        s.pull.unconsN(pullSize.toLong, false).flatMap {
          case Some((seg,tl)) => {
            // say(s"ressembling, pullsize is $pullSize")
            val data = seg.force.toArray
            // say(s"reassembling from ${data.map(_.toList).toList}")
            for(row <- 0 until pullSize){
              for(col <- 0 until (divisor*channels)){
                val internalSegOffset = row*divisor
                val offset = (col/divisor)*newSegmentLength
                val idx = (internalSegOffset + offset) + col%divisor
                // say(s"Attempting to collect from row: $row, col: $col")
                // say(s"With offsets calculated to: $internalSegOffset and $offset the final idx is $idx")
                // say(s"outBuf[$idx] <- data[$row][$col] = ${data(row)(col)}\n")
                outBuf(idx) = data(row)(col)
              }
            }
            // say(s"the reassembled pull is ${outbuf.tolist}")
            Pull.outputChunk(Chunk.seq(outBuf)) >> go(tl)
          }
          case None => Pull.done
        }
      }

      in => go(in).stream
    }

    in => in.through(disassemble).through(reassemble)

  }


  // Unsure about arg names here.
  def downsamplePipe[F[_],O](forEveryIncoming: Int, emitThisMany: Int): Pipe[F,O,O] = {

    val normalDropSize = forEveryIncoming/emitThisMany
    val normalEmits    = emitThisMany - (forEveryIncoming%emitThisMany)

    def go(step: Int, s: Stream[F,O]): Pull[F,O,Unit] = {
      val elementsToPull = normalDropSize + (if(step > normalEmits) 1 else 0)
      s.pull.unconsN(elementsToPull,true).flatMap {
        case Some((seg, tl)) => Pull.output1(seg.force.toList.head) >> go(step % emitThisMany, tl)
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


  def evalMapAsync[F[_]: Effect,I,O](f: I => F[O])(implicit ec: EC): Pipe[F,I,O] =
    s => s.through(_.map(z => Stream.eval(f(z)))).joinUnbounded

  def evalMapAsyncBounded[F[_]: Effect,I,O](n: Int)(f: I => F[O])(implicit ec: EC): Pipe[F,I,O] =
    s => s.through(_.map(z => Stream.eval(f(z)))).join(n)


  def say(word: Any, timestamp: Boolean = false)(implicit filename: sourcecode.File, line: sourcecode.Line): Unit = {
    import cyborg.io.files._, cats.effect.IO
    val fname = filename.value.split("/").last
    val timeString = if (timestamp) ", " + fileIO.getTimeStringUnsafe else ""
    println(Console.YELLOW + s"[${fname}: ${sourcecode.Line()}${timeString}]" + Console.RESET + s" - $word")
  }

  def Fsay[F[_]](word: Any)(implicit filename: sourcecode.File, line: sourcecode.Line, ev: Sync[F]): F[Unit] = {
    ev.delay{
      val fname = filename.value.split("/").last
      println(Console.YELLOW + s"[${fname}: ${sourcecode.Line()}]" + Console.RESET + s" - $word")
    }
  }


  def Ssay[F[_]](word: Any)(implicit filename: sourcecode.File, line: sourcecode.Line, ev: Sync[F]): Stream[F,Unit] = {
    Stream.eval(ev.delay{
                  val fname = filename.value.split("/").last
                  println(Console.YELLOW + s"[${fname}: ${sourcecode.Line()}]" + Console.RESET + s" - $word")
                })
  }



  def indexTupler[F[_],I]: Pipe[F,Seq[I],(I,Int)] =
    _.through(_.map(_.zipWithIndex)).through(chunkify)

}
