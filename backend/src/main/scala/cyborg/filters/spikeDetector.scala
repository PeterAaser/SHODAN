package cyborg

import fs2._
import cats.effect._
import cats.implicits._

object spikeDetector {

  /**
    operates on blocks of input of the size of a spike + refactory period (i.e input is blocked
    such that the pipe is capable of outputting the max amount of spikes possible exactly)

    The spike detector feeds its output to a moving average pipe which ensures that the agent can
    act somewhat consistantly inbetween detected spikes.
    */
  def spikeDetectorPipe[F[_]: Effect](getConf: F[Setting.FullSettings]): F[Pipe[F, Int, Double]] = getConf map { conf =>

    import conf.experimentSettings._
    import params.experiment.maxSpikesPerSec

    val spikeCooldown = (samplerate/maxSpikesPerSec)
    val threshold = conf.filterSettings.MAGIC_THRESHOLD

    /**
      spikeCooldownTimer: The refactory period between two spikes
      windowTimer:        How much remains of the current window

      the window must be the same length or shorter than the spike cooldown timer
      */
    def spikeDetector: Pipe[F, Boolean, Boolean] = {
      def go(spikeCooldownTimer: Int, s: Stream[F,Boolean]): Pull[F, Boolean, Unit] = {
        s.pull.unconsN(spikeCooldown.toLong) flatMap {
          case Some((seg, tl)) => {
            // say("spike detecting")
            val flattened = seg.force.toVector
            val refactored = flattened.drop(spikeCooldownTimer)
            val index = refactored.indexOf((λ: Boolean) => λ)
            if (index == -1) {
              // The case where none of the elements able to produce a spike triggered
              // For scala/java interop legacy reasons we have to use -1 instead of Option
              Pull.output1(false) >> go(0, tl)
            }
            else {
              // The case where a spike was triggered after cooldown lifted. The next
              // pull cooldown is calculated, and a spike is emitted
              val nextCooldown = spikeCooldown - (spikeCooldownTimer + index)
              Pull.output1(true) >> go(nextCooldown, tl)
            }
          }
          case None => Pull.done
        }
      }
      in => go(0, in).stream
    }


    (s: Stream[F,Int]) => s.through(_.map(_ > threshold))
      .through(spikeDetector)
      .through(_.map(λ => (if(λ) 1 else 0)))
      .through(utilz.fastMovingAverage(10))
  }

  /**
    * Spike detector pipe meant to use for plug and play with any
    * integer stream. Mostly useful for visualization and debugging
    * purposes, and not for real time analysis.
    */
  def unsafeSpikeDetector[F[_]: Effect](samplerate: Int,
    threshold: Int, replicate: Boolean = false): Pipe[F,Int,Int] = {
    val maxSpikesPerSec = params.experiment.maxSpikesPerSec
    val spikeCooldown = samplerate/maxSpikesPerSec

    def go(spikeCooldownTimer: Int, s: Stream[F,Int]): Pull[F,Int,Unit] = {
      s.pull.unconsN(spikeCooldown.toLong) flatMap {
        case None => Pull.done
        case Some((seg,tl)) => {
          val refactored = seg.force.toVector.drop(spikeCooldownTimer)
          val spike = refactored.dropWhile(x => x == 0)
          if (spike.length != 0) {
            val spikeSign = spike.head
            val nextCooldown = spikeCooldown - (spike.tail.length)
            Pull.output1(threshold*spikeSign) >> go(nextCooldown, tl)
          } else {
            Pull.output1(0) >> go(0, tl)
          }
        }
      }
    }

    def simpleDetector: Pipe[F,Int,Int] = _.map({x =>
      if (x > threshold) 1 else if (x < -threshold) -1 else 0})

    in => {
      val spikeStream = go(0, in.through(simpleDetector)).stream
      if (!replicate)
        spikeStream
      else
        spikeStream.through(utilz.replicateElementsPipe(spikeCooldown))
    }
  }
}
