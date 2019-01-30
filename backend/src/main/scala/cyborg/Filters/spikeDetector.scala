package cyborg

import cats.data.Kleisli
import fs2._
import cats._
import cats.effect._
import cats.implicits._
import utilz._

object spikeDetector {

  /**
    operates on blocks of input of the size of a spike + refactory period (i.e input is blocked
    such that the pipe is capable of outputting the max amount of spikes possible exactly)

    The spike detector feeds its output to a moving average pipe which ensures that the agent can
    act somewhat consistantly inbetween detected spikes.
    */
  def spikeDetectorPipe[F[_]] = Kleisli[Id, Settings.FullSettings, Pipe[F,Int,Double]](
    conf => {

      /**
        spikeCooldownTimer: The refactory period between two spikes
        windowTimer:        How much remains of the current window

        the window must be the same length or shorter than the spike cooldown timer
        */
      def spikeDetector: Pipe[F, Boolean, Boolean] = {
        def go(spikeCooldownTimer: Int, s: Stream[F,Boolean]): Pull[F, Boolean, Unit] = {
          s.pull.unconsN(conf.filter.spikeCooldown) flatMap {
            case Some((chunk, tl)) => {
              val refactored = chunk.drop(spikeCooldownTimer).toList
              val index = refactored.indexOf((x: Boolean) => x)
              if (index == -1) {
                Pull.output1(false) >> go(0, tl)
              }
              else {
                val nextCooldown = conf.filter.spikeCooldown - (spikeCooldownTimer + index)
                Pull.output1(true) >> go(nextCooldown, tl)
              }
            }
            case None => Pull.done
          }
        }
        in => go(0, in).stream
      }


      (s: Stream[F,Int]) => s.through(_.map(_ > conf.filter.threshold))
        .through(spikeDetector)
        .through(_.map(x => (if(x) 1 else 0)))
        .through(utilz.fastMovingAverage(10))
    })

  /**
    * Spike detector pipe meant to use for plug and play with any
    * integer stream. Mostly useful for visualization and debugging
    * purposes, and not for real time analysis.
    */
  def unsafeSpikeDetector[F[_]](samplerate: Int,
    threshold: Int, replicate: Boolean = false): Pipe[F,Int,Int] = {
    val maxSpikesPerSec = params.experiment.maxSpikesPerSec
    val spikeCooldown = samplerate/maxSpikesPerSec

    def go(spikeCooldownTimer: Int, s: Stream[F,Int]): Pull[F,Int,Unit] = {
      s.pull.unconsN(spikeCooldown) flatMap {
        case None => Pull.done
        case Some((chunk,tl)) => {
          val refactored = chunk.drop(spikeCooldownTimer)
          val spike = refactored.toList.dropWhile(x => x == 0)
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
