package cyborg

import cats.effect.{ Async, Concurrent, Effect, Sync, Timer }
import cats.implicits._
import cats._
import fs2._
import cats.effect.concurrent.Ref
import fs2.concurrent.{ InspectableQueue, Queue, Signal, SignallingRef, Topic }
import java.nio.{ ByteBuffer, ByteOrder }
import scala.concurrent.ExecutionContext

import scala.concurrent.duration._

import scala.language.higherKinds
import scala.reflect.ClassTag
import sourcecode._

object utilz {

  type EC = ExecutionContext

  type Channel = Int
  case class TaggedSegment(channel: Channel, data: Chunk[Int])
  type ChannelTopic[F[_]] = Topic[F,TaggedSegment]
  case class MuxedStream[F[_],I](numStreams: Int, stream: Stream[F,I])

  case class InterruptableAction[F[_]](interrupt: F[Unit], action: F[Unit])
  object InterruptableAction {
    def apply[F[_]: Concurrent](a: Stream[F,Unit]): F[InterruptableAction[F]] = {
      val interrupted = SignallingRef[F,Boolean](false)
      interrupted.map { interruptSignal =>
        InterruptableAction(
          interruptSignal.set(true),
          a.interruptWhen(interruptSignal.discrete).compile.drain
        )}
    }
  }


  implicit class ChunkBonusOps[I](c: Chunk[I]) {
    def ++(that: Chunk[I]): Chunk[I] = Chunk.concat(List(c, that))
    def foldMonoid[I2 >: I](implicit I: Monoid[I2]): I2 =
      c.foldLeft(I.empty)(I.combine)
  }
  implicit val intAdditionMonoid: Monoid[Int] = new Monoid[Int] {
    def empty: Int = 0
    def combine(x: Int, y: Int): Int = x + y
  }
  implicit class StreamBonusOps[F[_],O](s: Stream[F,O]) {
    def vecN(n: Int, allowFewer: Boolean = true): Stream[F,Vector[O]] =
      s.repeatPull {
        _.unconsN(n, allowFewer).flatMap {
          case Some((hd, tl)) => Pull.output1(hd.toVector).as(Some(tl))
          case None           => Pull.pure(None)
        }
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
        case Some((chunk, tl)) => {
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


  def bytesToIntsJVM[F[_]]: Pipe[F, Byte, Int] = {

    def go(s: Stream[F, Byte]): Pull[F,Int,Unit] = {
      s.pull.unconsN(4096, false)flatMap {
        case Some((seg, tl)) => {
          val data = seg.toArray
          val intbuf = ByteBuffer.wrap(data)
            .order(ByteOrder.BIG_ENDIAN)
            .asIntBuffer()
          val outs = Array.ofDim[Int](1024)
          intbuf.get(outs)
          val huh = Chunk.seq(outs)
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
        case Some((chunk, tl)) => {
          val data = chunk.toArray
          val bb = ByteBuffer.allocate(4096)
          bb.asIntBuffer().put(data)
          val outs = bb.array()
          val huh = Chunk.seq(outs)
          Pull.output(huh) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in).stream
  }


  /**
    */
  def mapN[F[_],I,O](length: Int, f: Chunk[I] => O): Pipe[F,I,O] = {
    def go(s: Stream[F,I]): Pull[F,O,Unit] = {
      s.pull.unconsN(length, false).flatMap {
        case Some((chunk, tl)) => {
          Pull.output1(f(chunk)) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in).stream
  }


  /**
    This thing should be a method on stream
    */
  def chunkify[F[_],I]: Pipe[F,Chunk[I],I] = {

    def go(s: Stream[F,Chunk[I]]): Pull[F,I,Unit] = {
      s.pull.uncons1 flatMap {
        case Some((chunk, tl)) =>
          Pull.output(chunk) >> go(tl)
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
  def stridedSlide[F[_],I](windowWidth: Int, overlap: Int): Pipe[F,I,Chunk[I]] = {
    require(windowWidth > 0,       "windowWidth must be > 0")
    require(windowWidth > overlap, "windowWidth must be wider than overlap")
    val stepsize = windowWidth - overlap
    def go(s: Stream[F,I], last: Chunk[I]): Pull[F,Chunk[I],Unit] = {
      s.pull.unconsN(stepsize, false).flatMap {
        case Some((chunk, tl)) =>
          val concatenated2 = last ++ chunk
          val concatenated = Chunk.concat(List(last, chunk))
          Pull.output1(concatenated) >> go(tl, concatenated.drop(stepsize))
        case None => Pull.done
      }
    }

    in => in.pull.unconsN(windowWidth, false).flatMap {
      case Some((chunk, tl)) => {
        Pull.output1(chunk) >> go(tl, chunk.drop(stepsize))
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

    def go(s: Stream[F,Int], window: Chunk[Int]): Pull[F,Double,Unit] = {
      s.pull.uncons flatMap {
        case Some((chunk, tl)) => {
          val stacked = ((window ++ chunk) zip (chunk)).map(z => (z._1 - z._2))
          val scanned = stacked.scanLeft(window.foldMonoid)(_+_).map(_.toDouble/windowWidth.toDouble)
          Pull.output(scanned) >> go(tl, Chunk.Queue(chunk).takeRight(windowWidth).toChunk)
        }
        case None => Pull.done
      }
    }

    in => in.pull.unconsN(windowWidth).flatMap {
      case Some((chunk, tl)) => {
        Pull.output1(chunk.foldLeft(0)(_+_).toDouble/windowWidth.toDouble) >> go(tl, chunk)
      }
      case None => Pull.done
    }.stream
  }


  /**
    * SMA as unweighted mean of previous n data points. Maps content
    * into absolute values for spike detection.
    */
  def simpleMovingAverage[F[_]](windowWidth: Double): Pipe[F,Int,Double] = {
    def go(s: Stream[F,Int]): Pull[F,Double,Unit] = {
      s.pull.unconsN(windowWidth.toInt) flatMap {
        case None => Pull.done
        case Some((chunk,tl)) => {
          val avg = chunk.foldMonoid.toDouble / chunk.size.toDouble
          Pull.output1(avg) >> go(tl)
        }
      }
    }

    in => go(in.map(math.abs(_))).stream
  }


  /**
    Creates a list containing num topics of type T
    Requires an initial message init.
    */
  def createTopics[F[_]: Concurrent, T](init: T): Stream[F,Topic[F,T]] = {
    val topicTask: F[Topic[F,T]] = Topic[F,T](init)
    Stream.repeatEval(topicTask)
  }


  /**
    Does not account for rounding errors!

    */
  def throttlerPipe[F[_]: Concurrent : Timer,I](elementsPerSec: Int, resolution: FiniteDuration): Pipe[F,I,I] = {
    val ticksPerSecond = (1.second/resolution)
    val elementsPerTick = (elementsPerSec/ticksPerSecond).toInt

    _.chunkN(elementsPerTick, false)
      .metered(resolution)
      .through(chunkify)
  }


  /**
    Takes a stream of lists of streams and converts it into a single stream
    by selecting output from each input stream in a round robin fashion.

    Not built for speed, at least not when chunksize is low

    At the moment round robin is a special case and is thus allowed to maintain
    its Seq[I] signature rather than Chunk[I] to make it harder to accidentally
    unchunk it.
    */
  def roundRobin[F[_],I]: Pipe[F,List[Stream[F,I]],List[I]] = _.flatMap(roundRobinL)


  def roundRobinL[F[_],I](streams: List[Stream[F,I]]): Stream[F,List[I]] = {
    def zip2List(a: Stream[F,I], b: Stream[F,List[I]]): Stream[F,List[I]] = {
      a.zipWith(b)(_::_)
    }
    val mpty: Stream[F,List[I]] = Stream(List[I]()).repeat
    streams.foldRight(mpty)((z,u) => zip2List(z,u))
  }


  def flatRoundRobinL[F[_],I](streams: List[Stream[F,I]]): MuxedStream[F,I] = {
    val muxedStream = roundRobinL(streams)
    MuxedStream(streams.length, muxedStream.map(Chunk.seq).through(chunkify))
  }


  // TODO High risk of fuckup after version bump
  def roundRobinQ[F[_]: Concurrent, I:ClassTag](streams: List[Stream[F,I]]): Stream[F,Chunk[I]] = {

    val numStreams = streams.size
    val elementsPerDeq = 20

    Stream.repeatEval(Queue.bounded[F,Chunk[I]](100)).chunkN(numStreams).take(1) flatMap { qs =>
      Stream.eval(Queue.bounded[F,Chunk[I]](10)) flatMap { outQueue =>

        val inputTasks = (qs zip Chunk(streams)).map{ case(q, s) => s.map(_.chunkN(elementsPerDeq).through(q.enqueue).compile.drain) }
        val huh = inputTasks.map(Chunk.seq).toList
        val inputTask = Stream.emits(huh).through(chunkify).covary[F].map(Stream.eval).parJoin(numStreams)

        val arr = Array.ofDim[I](elementsPerDeq, numStreams)

        def outputFillTask: F[Unit] = {

          def unloader(idx: Int): F[Unit] = {
            if(idx == numStreams)
              loader(0)
            else {
              outQueue.enqueue1(Chunk.seq(arr(idx))) >> unloader(idx + 1)
            }
          }

          def loader(idx: Int): F[Unit] = {
            if(idx == numStreams) {
              unloader(0)
            }
            else {
              (qs(idx).dequeue1 map { xs: Chunk[I] =>
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


  def logEveryNth[F[_],I](n: Int): Pipe[F,I,I] = {
    def go(s: Stream[F,I]): Pull[F,I,Unit] = {
      s.pull.unconsN(n,false) flatMap {
        case Some((chunk, tl)) => {
          say(s"${chunk.head}")
          Pull.output(chunk) >> go(tl)
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
      s.pull.unconsN(n,false) flatMap {
        case Some((chunk, tl)) => {
          message(chunk.head.get) // RIP NonEmptyChunk
          Pull.output(chunk) >> go(tl)
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
  def spamQueueSize[F[_]: Concurrent : Timer, I](
    name: String,
    q: InspectableQueue[F,I],
    dur: FiniteDuration): Stream[F,Unit] = {

    Stream.fixedRate(dur).flatMap { _ =>
      q.size.through(_.map(z => say(s"Queue with name $name has a size of $z")))
    }.repeat
  }


  def tagPipe[F[_]](segmentLength: Int): Pipe[F, Int, TaggedSegment] = {
    def go(n: Channel, s: Stream[F,Int]): Pull[F,TaggedSegment,Unit] = {
      s.pull.unconsN(segmentLength, false) flatMap {
        case Some((chunk, tl)) => {
          Pull.output1(TaggedSegment(n, chunk)) >> go((n + 1) % 60, tl)
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
      s.pull.unconsN(segmentLength, false) flatMap {
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
      s.pull.unconsN(factor, false) flatMap {
        case None => Pull.done
        case Some((chunk,tl)) => {
          Pull.output1(chunk.head.get) >> go(tl) // RIP NonEmptyChunk
        }
      }
    }

    in => go(in).stream
  }


  /**
    * Replicate each element n times, and subsequently flatten the
    * resulting Stream.
    */
  def replicateElementsPipe[F[_]](n: Int): Pipe[F,Int,Int] = {
    def go(s: Stream[F,Int]): Pull[F,Int,Unit] = {
      s.pull.uncons1 flatMap {
        case None => Pull.done
        case Some((hd,tl)) => {
          Pull.output(Chunk.seq(Seq.fill(n)(hd))) >> go(tl)
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
  def vizSink[F[_]: Sync](samplerate: Int, resolution: FiniteDuration = 0.1.second,
    numStreams: Int = 1): Sink[F, Int] = {
    val plot = new ReservoirPlot.TimeSeriesPlot(
      Array.fill(ReservoirPlot.getSlidingWindowSize(samplerate, resolution))(0.0f),
      samplerate, resolution
    )
    plot.show

    // Ideally we'd like to also add the main series here as well.
    (0 until numStreams - 1).map(_ => {
      plot.addStream(Array.fill(plot.slidingWindowSize)(0.0f))
    })

    // Note that the vectorization is of size samplerate, to make sure
    // it's unlikely that Timer in ReservoirPlot will ever catch up.
    _.vecN(samplerate*numStreams).through(
      Sink.apply(xs =>
        Sync[F].delay(
          {
            // Not thread safe (do we care?)
            val extensions = xs.grouped(numStreams).map(_.toArray).toArray.transpose
            for ((extension, i) <- extensions.zipWithIndex) {
              plot.extendStream(i, extension.map(_.toFloat))
            }
          }
        )
      )
    )
  }


  def attachVizPipe[F[_]: Concurrent](samplerate: Int, resolution: FiniteDuration = 0.1.second,
                                      numStreams: Int = 1): Pipe[F,Int,Int] =
    _.observeAsync(1024)(vizSink(samplerate, resolution, numStreams))


  /**
    Modifies the segment length of a stream.
    Can be optimized further if needed, for instance using chunk instead of list.
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
        s.pull.unconsN(channels*originalSegmentLength, false).flatMap {
          case Some((chunk,tl)) => {
            // say("disassembling")
            for(i <- 0 until pullSize){
              val outRow = (i/divisor)%outRows
              // val outRow = (i/outCols)
              val outCol = (i%divisor) + divisor*(i/originalSegmentLength)
              // say(s"with i: $i we get outBuf[$outRow][$outCol] <- ${chunk(i)}")
              outBuffers(outRow)(outCol) = chunk(i)
            }
            // say(s"disassembler outputting ${outBuffers.map(_.toList).toList} \n")
            Pull.output(Chunk.seq(outBuffers.map(_.toList).toList)) >> go(tl)
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
        s.pull.unconsN(pullSize, false).flatMap {
          case Some((chunk,tl)) => {
            // say(s"ressembling, pullsize is $pullSize")
            // say(s"reassembling from ${chunk.map(_.toList).toList}")
            for(row <- 0 until pullSize){
              for(col <- 0 until (divisor*channels)){
                val internalSegOffset = row*divisor
                val offset = (col/divisor)*newSegmentLength
                val idx = (internalSegOffset + offset) + col%divisor
                // say(s"Attempting to collect from row: $row, col: $col")
                // say(s"With offsets calculated to: $internalSegOffset and $offset the final idx is $idx")
                // say(s"outBuf[$idx] <- chunk[$row][$col] = ${chunk(row)(col)}\n")
                outBuf(idx) = chunk(row)(col)
              }
            }
            // say(s"the reassembled pull is ${outbuf.tolist}")
            Pull.output(Chunk.seq(outBuf)) >> go(tl)
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

    val normalDropSize = forEveryIncoming / emitThisMany
    val normalEmits    = emitThisMany - (forEveryIncoming % emitThisMany)

    def go(step: Int, s: Stream[F,O]): Pull[F,O,Unit] = {
      val elementsToPull = normalDropSize + (if(step > normalEmits) 1 else 0)
      s.pull.unconsN(elementsToPull,true).flatMap {
        case Some((chunk, tl)) => Pull.output1(chunk.head.get) >> go(step % emitThisMany, tl) // RIP NonEmptyChunk
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


  def evalMapAsync[F[_]: Concurrent,I,O](f: I => F[O]): Pipe[F,I,O] =
    s => s.through(_.map(z => Stream.eval(f(z)))).parJoinUnbounded

  def evalMapAsyncBounded[F[_]: Concurrent,I,O](n: Int)(f: I => F[O]): Pipe[F,I,O] =
    s => s.through(_.map(z => Stream.eval(f(z)))).parJoin(n)


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


  def timeStamp[F[_]: Concurrent](implicit ev: Timer[F]): Pipe[F,String,String] = {
    val getTime = ev.clock.monotonic(MILLISECONDS)
    _.evalMap(s => getTime.map(time => time + ":" + s + "\n"))
  }
}
