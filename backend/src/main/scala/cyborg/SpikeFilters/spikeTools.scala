package cyborg

import scala.concurrent.duration._
import scala.collection.mutable.{ Queue => MutableQueue }

import cats.data.Kleisli
import fs2._
import fs2.concurrent.Topic
import fs2.concurrent.Queue
import cats._
import cats.effect._
import cats.implicits._
import utilz._

import Settings._

class SpikeTools[F[_]: Concurrent](kernelWidth: FiniteDuration, conf: FullSettings, viz: VizState) {

  lazy val bucketSize = hardcode(100.millis)
  lazy val buckets = hardcode(10)

  val kernelSize = {
    val size = (conf.daq.samplerate.toDouble*(kernelWidth.toMillis.toDouble/1000.0)).toInt
    if(size % 2 == 0) size + 1 else size
  }

  /**
    For Spike detectan
    */
  val pointsPerBucket = (conf.daq.samplerate.toDouble*(bucketSize.toMillis.toDouble/1000.0)).toInt // ???
  say(s"points per bucket: $pointsPerBucket")
  say(s"kernel size: $kernelSize")


  /**
    The gaussian blur convolutor not only outputs the convoluted value, it also outputs the raw input
    stream as a chunk aligned with the smoothed curve
    
    Not sure if this thing is very good. Can't the API just be zip anyways?
    Sure, syncing isn't 100% trivial, but it's better than this unreadable clusterfuck
    */
  def gaussianBlurConvolutor: Pipe[F,Int,Int] = {

    def go(s: Stream[F,Int], q: MutableQueue[Int], sum: Int): Pull[F, Int, Unit] = {
      s.pull.uncons.flatMap {
        case Some((chunk, tl)) => {

          val convBuffer = Array.ofDim[Int](chunk.size)
          var sumRef = sum
          for(ii <- 0 until chunk.size){
            convBuffer(ii) = sumRef/kernelSize
            sumRef += (chunk(ii) - q.dequeue())
            q.enqueue(chunk(ii))
          }
          Pull.output(Chunk.ints(convBuffer)) >>
            go(tl, q, sumRef)
        }
        case None => say("pull ded"); Pull.done
      }
    }

    def init(s: Stream[F,Int]): Pull[F, Int, Unit] = {
      s.pull.unconsN(kernelSize, false) flatMap {
        case Some((chunk, tl)) => {
          go(tl, MutableQueue[Int]() ++= chunk.toArray, chunk.foldMonoid)
        }
        case None => say("pull ded"); Pull.done
      }
    }
    inStream => init(inStream).stream
  }


  def avgNormalizer(raw: Stream[F,Int], convoluted: Stream[F,Int]): Stream[F,Int] = {
    raw.drop(kernelSize/2).zip(convoluted).map{ case(raw, averaged) => raw - averaged}
  }


  /**
    Calculates an array of spike/not-spike for visualizing
    
    Sans cooldown
    */
  def spikeDetectorPipe: Pipe[F,Int,Boolean] = {

    lazy val thresh = hardcode(1700)

    def go(s: Stream[F,Int], canSpike: Boolean): Pull[F,Boolean,Unit] =
      s.pull.uncons.flatMap {
        case Some((chunk, tl)) => {
          val spikes = Array.ofDim[Boolean](chunk.size)
          var canSpikeRef = canSpike
          for(ii <- 0 until chunk.size){
            if(canSpikeRef && math.abs(chunk(ii)) > thresh){
              spikes(ii) = true
              canSpikeRef = false
            }
            else if(!canSpikeRef && chunk(ii) < thresh){
              spikes(ii) = false
              canSpikeRef = true
            }
            else
              spikes(ii) = false
          }
          Pull.output(Chunk.booleans(spikes)) >> go(tl, canSpikeRef)
        }
        case None => Pull.doneWith("spike detector is dead!")
      }

    inStream => go(inStream, true).stream
  }




  def spikePipe: Pipe[F,Chunk[Int],Int] = { inputs =>
    Stream.eval(Queue.unbounded[F,Chunk[Int]]).flatMap{ q1 =>
      Stream.eval(Queue.unbounded[F,Chunk[Int]]).flatMap{ q2 =>
        val ins = inputs
          .observe(q1.enqueue)
          .through(q2.enqueue)

        val raw        = q2.dequeue.hideChunks
        val blurred    = q1.dequeue.hideChunks.through(gaussianBlurConvolutor)
        val normalized = avgNormalizer(raw, blurred)
        val spikes     = normalized.through(spikeDetectorPipe)
        val outs       = spikes.mapN(_.foldMap(b => if(b) 1 else 0), pointsPerBucket)

        outs.concurrently(ins)
      }
    }
  }

  def outputSpikes(streams: Chunk[Stream[F,Chunk[Int]]]): Stream[F,Chunk[Int]] = {
    val spikeBuckets = streams.map(_.through(spikePipe)).toList
    roundRobinC(spikeBuckets)
  }
}
