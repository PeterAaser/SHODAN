package cyborg

import scala.concurrent.duration._
import scala.collection.mutable.{ Queue => MutableQueue }

import cats.data.Kleisli
import fs2.{ Chunk, _ }
import cats._
import cats.effect._
import cats.implicits._
import utilz._

import Settings._


class SpikeTools[F[_]](kernelWidth: FiniteDuration, settings: FullSettings) {

  val frameSizeM = hardcode(0.05.second)
  val pointsPerFrame = (settings.daq.samplerate * (frameSizeM.toMillis.toDouble/1000.0)).toInt

  val kernelSize = {
    val size = (settings.daq.samplerate*(kernelWidth.toMillis.toDouble/1000.0)).toInt
    if(size % 2 == 0) size + 1 else size
  }


  // pretty arbitrary
  val elementsPerChunk = (pointsPerFrame/kernelSize)


  // How many elements of raw data we pull per frame
  val frameSize = hardcode(500)
  val totalFrames = hardcode(10)

  /**
    The gaussian blur convolutor not only outputs the convoluted value, it also outputs the raw input
    stream as a chunk aligned with the smoothed curve

    cbf making it general enough to uncons without exploding
    */
  private def gaussianBlurConvolutor: Pipe[F,Int,(Chunk[Int], Chunk[Int])] = {

    val cutoff = (kernelSize/2) + 1

    // It's a rounding trick yo
    val pointsPerPull = (pointsPerFrame/kernelSize)*kernelSize

    def go(s: Stream[F,Int], q: MutableQueue[Int], sum: Int): Pull[F, (Chunk[Int], Chunk[Int]), Unit] = {
      s.pull.unconsN(pointsPerPull, false).flatMap {
        case Some((chunk, tl)) => {

          val convBuffer = Array.ofDim[Int](chunk.size - (cutoff))
          var sumRef = sum
          for(ii <- 0 until chunk.size - (cutoff)){
            convBuffer(ii) = sumRef/kernelSize
            sumRef = sumRef + chunk(ii + cutoff) - q.dequeue()
            q.enqueue(chunk(ii + cutoff))
          }
          Pull.output1((chunk.take(chunk.size - (cutoff)), Chunk.ints(convBuffer))) >>
            go(tl.cons(chunk.drop(chunk.size - (cutoff))), q, sumRef)
        }
        case None => Pull.done
      }
    }

    def init(s: Stream[F,Int]): Pull[F, (Chunk[Int], Chunk[Int]), Unit] = {
      s.pull.unconsN(kernelSize, false) flatMap {
        case Some((chunk, tl)) => {
          val rest = chunk.drop(cutoff - 1)
          go(tl.cons(rest), MutableQueue[Int]() ++= chunk.toArray, chunk.foldMonoid)
        }
        case None => Pull.done
      }
    }

    inStream => init(inStream).stream
  }

  // TODO Ensure that raw size is frameSize, or if not change it downstream
  private def avgNormalizer: Pipe[F,(Chunk[Int], Chunk[Int]), Int] = {
    def go(s: Stream[F,(Chunk[Int],Chunk[Int])]): Pull[F,Int,Unit] = {
      s.pull.uncons1.flatMap {
        case Some(((raw, convoluted), tl)) => {
          say(s"raw size is: ${raw.size}")
          val buf = Array.ofDim[Int](raw.size)
          for(ii <- 0 until raw.size){
            buf(ii) = raw(ii) - convoluted(ii)
          }
          Pull.output(Chunk.ints(buf)) >> go(tl)
        }
        case None => Pull.done
      }
    }
    inStream => go(inStream).stream
  }


  /**
    Calculates the amount of spikes in given frame
    If framesize is set to 500 this is equivalent to a 50ms window in a 10khz stream
    */
  private def spikeDetectorPipe: Pipe[F,Int,Int] = {

    val thresh = hardcode(100)
    val cooldown = hardcode(100)

    def go(s: Stream[F,Int], cooldown: Int): Pull[F,Int,Unit] =
      s.pull.unconsN(pointsPerFrame, false) flatMap {
        case Some((chunk, tl)) => {
          val (spikes, nextCooldown) = chunk.foldLeft((0, cooldown)){ case((spikesAcc, cd), voltage) =>
            if(cd > 0)
              (spikesAcc, cd - 1)
            else if(math.abs(voltage) > thresh)
              (spikesAcc + 1, cooldown)
            else
              (spikesAcc, cooldown)
          }
          Pull.output1(spikes) >> go(tl, cooldown)
        }
      }

    inStream => go(inStream, 0).stream
  }


  /**
    Aggregates the spikes from a list of channels.
    */
  private def spikeAggregator(sourcesL: List[Stream[F,Int]]): Stream[F,Int] = {

    val sources = sourcesL.toArray
    val outBuf = Array.ofDim[Int](60)

    def loop(idx: Int): Pull[F,Int,Unit] = {
      if(idx < 60){
        sources(idx).pull.uncons1.flatMap {
          case Some((spikeCount, tl)) => {
            sources(idx) = tl
            outBuf(idx) = spikeCount
            loop(idx + 1)
          }
        }
      }
      else
        Pull.output(Chunk.ints(outBuf)) >> loop(0)
    }

    loop(0).stream
  }

  /**
    Windows the aggregated spikes
    */
  private def spikeWindowBundler: Pipe[F,Int,Chunk[Int]] = { inStream =>
    inStream.through(stridedSlide(60*totalFrames, 60))
  }
}
