package cyborg


import cats.effect.IO
import cats.effect._

import MEAMEutilz._
import scala.concurrent.duration._
import utilz._
import fs2._
import params._
import bonus._


object PerturbationTransform {

  // (TODO) (thomaav): Move me somewhere more fitting?
  /**
    * Linearly transforms a sight range to a pulse period.
    *
    *            (d-c)
    * f(t) = c + ----- (t-a), from [a,b] to [c,d]
    *            (b-a)
    */
  def linearTransform(range: Double): Option[FiniteDuration] = {
    // Mind the ns resolution -- should perhaps be rounded
    val period = 1.0 /
      (perturbationTransform.scaleRangeToFreq * (range - game.deadZone) + experiment.minFreq)
    if (game.deadZone <= range && range <= game.sightRange) Some(period.seconds) else None
  }


  /** Checks whether a given threshold for the change of sight range
    * for an agent is exceeded.
    */
  def rangeThreshExceeded(threshold: Double, prev: List[Double], cur: List[Double])
      : Boolean = {
    prev.zipWith(cur)((x,y) => math.abs(y-x) >= threshold).foldLeft(false)(_ || _)
  }

  // (TODO) (thomaav): Think about how min/max ranges should affect this
  /** Transforms agent sight ranges to stimuli pulses. The default
    * transform is a linear transform.
    *
    * Note that the output may consist of only None if sight ranges
    * that trigger a new pulse are outside the min/max ranges of the
    * agent.
    */
  def toStimReq[F[_]](transform: Double => Option[FiniteDuration] = linearTransform)
      : Pipe[F,List[Double], List[(Int, Option[FiniteDuration])]] = {

    def go(prev: List[Double], s: Stream[F, List[Double]])
        : Pull[F, List[(Int, Option[FiniteDuration])], Unit] = {
      s.pull.uncons1.flatMap {
        case None => Pull.done
        case Some((sr,tl)) => {
          if (rangeThreshExceeded(100.0, prev, sr))
            Pull.output1(sr.zipWithIndex.map(x => (x._2, transform(x._1)))) >> go(sr, tl)
          else
            go(prev, tl)
        }
      }
    }

    def init(s: Stream[F, List[Double]])
        : Pull[F, List[(Int, Option[FiniteDuration])], Unit] = {
      s.pull.uncons1.flatMap {
        case None => Pull.done
        case Some((sr,tl)) => go(sr,tl)
      }
    }

    in => init(in).stream
  }
}
