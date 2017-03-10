package com.cyborg

import fs2._

object spikeDetector {


  def singleSpikeDetectorPipe[F[_]](f: Vector[Int] => Boolean): Pipe[F,Vector[Int],Boolean] = {
    def go: Handle[F,Vector[Int]] => Pull[F,Boolean,Unit] = h => {
      h.receive1 { case (a, h) =>
        Pull.output1(f(a)) >> go(h)
      }
    }
    _ pull go
  }

  def simpleDetector(datapoints: Vector[Int]): Boolean = {
    val voltageThreshold = 10000
    val spikeThreshold = 100
    (datapoints count (math.abs(_) > voltageThreshold)) > spikeThreshold
  }


  def spikeDetectorPipe[F[_]](period: Int, sampleRate: Int, threshold: Int): Pipe[F, Int, Int] = {

    val cooldown: Long = 100 // should be a function of sample rate

    // Rules for coalescing:
    // If spiked, there is a cooldown until next time a spike may be triggered
    // Might add more rules, who knows?
    def coalescingPipe: Pipe[F, Int, Boolean] = {
      def go(cd: Boolean): Handle[F, Boolean] => Pull[F, Boolean, Unit] = h => {
        if(cd)
          h.take(cooldown) flatMap { h => go(false)(h) }
        else
          h.takeThrough(λ => !λ) flatMap { h => go(true)(h) }
      }
      _.map(_ > threshold).pull(go(false))
    }


    def go: Handle[F,Boolean] => Pull[F,Int,Unit] = h => {
      h.awaitN(period) flatMap {
        case (chunks, h) =>
          {
            Pull.output1(chunks.map(_.foldLeft(0)((λ, µ) => {if(µ) λ + 1 else λ})).sum) >> go(h)
          }
      }
    }

    _.through(coalescingPipe).pull(go)
  }
}
