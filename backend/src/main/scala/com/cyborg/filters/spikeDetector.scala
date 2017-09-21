package com.cyborg

import fs2._

object spikeDetector {

  def singleSpikeDetectorPipe[F[_]](f: Vector[Int] => Boolean): Pipe[F,Vector[Int],Boolean] = {
    def go(s: Stream[F,Vector[Int]]): Pull[F,Boolean,Unit] = {
      s.pull.uncons1 flatMap {
        case Some((v, tl)) =>
          Pull.output1(f(v)) >> go(tl)
        case None => Pull.done
      }
    }
    in => go(in).stream
  }

  def simpleDetector(datapoints: Vector[Int]): Boolean = {
    val voltageThreshold = 10000
    val spikeThreshold = 100
    (datapoints count (math.abs(_) > voltageThreshold)) > spikeThreshold
  }


  /**
    operates on blocks of input of the size of a spike + refactory period (i.e input is blocked
    such that the pipe is capable of outputting the max amount of spikes possible exactly)

    The spike detector feeds its output to a moving average pipe which ensures that the agent can
    act somewhat consistantly inbetween detected spikes.
    */
  def spikeDetectorPipe[F[_]](sampleRate: Int, threshold: Int): Pipe[F, Int, Double] = s => {

    import params.experiment._

    val spikeCooldown = (samplerate/maxSpikesPerSec) // should be a function of sample rate

    /**
      spikeCooldownTimer: The refactory period between two spikes
      windowTimer:        How much remains of the current window

      the window must be the same length or shorter than the spike cooldown timer
      */
    def spikeDetector: Pipe[F, Boolean, Boolean] = {
      def go(spikeCooldownTimer: Int, s: Stream[F,Boolean]): Pull[F, Boolean, Unit] = {
        s.pull.unconsN(spikeCooldown) flatMap {
          case Some((seg, tl)) => {
            val flattened = seg.toVector
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
        }
      }
      in => go(0, in).stream
    }


    s
      .through(_.map(_ > threshold))
      .through(spikeDetector)
      .through(_.map(λ => (if(λ) 1 else 0)))
      .through(utilz.fastMovingAverage(10))
  }
}
