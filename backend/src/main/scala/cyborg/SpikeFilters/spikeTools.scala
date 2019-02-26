package cyborg

import scala.concurrent.duration._
import scala.collection.mutable.{ Queue => MutableQueue }

import cats.data.Kleisli
import fs2._
import fs2.concurrent.Topic
import cats._
import cats.effect._
import cats.implicits._
import utilz._

import Settings._

class SpikeTools[F[_]: Concurrent](kernelWidth: FiniteDuration, conf: FullSettings) {

  lazy val bucketSize = hardcode(100.millis)
  lazy val buckets = hardcode(10)

  val kernelSize = {
    val size = (conf.daq.samplerate.toDouble*(kernelWidth.toMillis.toDouble/1000.0)).toInt
    if(size % 2 == 0) size + 1 else size
  }


  /**
    For drawing
    */
  val canvasPoints = 1000
  val canvasPointLifetime = 5000.millis
  val pointsNeededPerSec = (canvasPoints.toDouble/canvasPointLifetime.toSeconds.toDouble).toInt //200
  val pointsPerDrawcall = conf.daq.samplerate/pointsNeededPerSec // 50 for 10khz

  /**
    For Spike detectan
    */
  val pointsPerBucket = (conf.daq.samplerate.toDouble*(bucketSize.toMillis.toDouble/1000.0)).toInt // ???
  say(s"points per bucket: $pointsPerBucket")



  /**
    The gaussian blur convolutor not only outputs the convoluted value, it also outputs the raw input
    stream as a chunk aligned with the smoothed curve
    
    Not sure if this thing is very good. Can't the API just be zip anyways?
    Sure, syncing isn't 100% trivial, but it's better than this unreadable clusterfuck

    */
  private def gaussianBlurConvolutor: Pipe[F,Int,Int] = {

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


  private def avgNormalizer(raw: Stream[F,Int], convoluted: Stream[F,Int]): Stream[F,Int] = {
    raw.drop(kernelSize/2).zip(convoluted).map{ case(raw, averaged) => raw - averaged}
  }


  /**
    Calculates an array of spike/not-spike for visualizing
    
    Sans cooldown
    */
  private def spikeDetectorPipe2: Pipe[F,Int,Boolean] = {

    lazy val thresh = hardcode(2000)

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


  import cyborg.RPCmessages.DrawCommand
  private def visualizeRaw(hi: Int, lo: Int) = DrawCommand(hi, lo, 0)
  private def visualizeAvg(hi: Int, lo: Int) = DrawCommand(hi, lo, 1)
  

  /**
    Sure as hell ain't pretty...
    */
  import cyborg.RPCmessages._
  def visualizeRawAvg: Pipe[F, Int, Array[Array[DrawCommand]]] = { inputs =>

    val rawStream = inputs.drop((kernelSize/2) + 1)
    val rawStreamViz = rawStream
      .through(downsampleHiLoWith(pointsPerDrawcall, visualizeRaw))

    val blurred = inputs.through(gaussianBlurConvolutor)
    val blurredViz = blurred
      .through(downsampleHiLoWith(pointsPerDrawcall, visualizeAvg))

    (rawStreamViz zip blurredViz).map{ case(a,b) => 
      Array(a,b)
    }.mapN(_.toArray, 50)
  }


  def visualizeNormSpikes: Pipe[F,Int,Array[Array[DrawCommand]]] = { inputs =>
    val blurred = inputs.through(gaussianBlurConvolutor)
    val avgNormalized = avgNormalizer(inputs, blurred)
    val avgNormalizedViz = avgNormalized
      .through(downsampleHiLoWith(pointsPerDrawcall, visualizeRaw))

    val spikes = avgNormalized.through(spikeDetectorPipe2)
    val spikesViz = spikes.through(downsampleWith(pointsPerDrawcall, 1)((c: Chunk[Boolean]) => c.foldLeft(false)(_||_)))
      .map(b => if(b) DrawCommand(-2600, -10000, 1) else DrawCommand(0,0,0))

    (avgNormalizedViz zip spikesViz).map{ case(a,b) => 
      Array(a,b)
    }.mapN(_.toArray, 50)
  }


  def outputSpike(topic: Topic[F,Chunk[Int]]): Stream[F,Int] = {
    val inputs = topic.subscribe(10).hideChunks
    val blurred = inputs.through(gaussianBlurConvolutor)
    val avgNormalized = avgNormalizer(inputs, blurred)
    val spikes = avgNormalized.through(spikeDetectorPipe2)
    val bucketed = spikes.mapN(_.foldMap(b => if(b) 1 else 0), pointsPerBucket)
    bucketed
  }

  def outputSpikes(topics: List[Topic[F,Chunk[Int]]]): Stream[F,Chunk[Int]] = {
    val spikeBuckets = topics.map(outputSpike)
    roundRobinC(spikeBuckets)
  }
}

object SpikeTools {
  def kleisliConstructor[F[_]: Concurrent](width: FiniteDuration): Kleisli[F, FullSettings, SpikeTools[F]] = Kleisli[F, FullSettings, SpikeTools[F]]{ conf =>
    Sync[F].delay(new SpikeTools[F](width, conf))
  }
}
