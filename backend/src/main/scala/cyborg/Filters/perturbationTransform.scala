package cyborg


import cats.effect.IO
import cats.effect._

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
  lazy val threshold = hardcode(100.0)
  def rangeThreshExceeded(prev: Double, cur: Double): Boolean =
    math.abs(prev-cur) >= threshold


  /** Transforms agent sight ranges to stimuli pulses. The default
    * transform is a linear transform.
    *
    * Note that the output may consist of only None if sight ranges
    * that trigger a new pulse are outside the min/max ranges of the
    * agent.
    */
  def toStimReq[F[_]](transform: Double => Option[FiniteDuration] = linearTransform)
      : Pipe[F,(Int, Double), (Int, Option[FiniteDuration])] = {

    def go(prev: List[Double], s: Stream[F, (Int, Double)])
        : Pull[F, (Int, Option[FiniteDuration]), Unit] = {
      s.pull.uncons1.flatMap {
        case None => Pull.done
        case Some(((idx, range), tl)) if(rangeThreshExceeded(range, prev(idx))) =>
          Pull.output1((idx, transform(range))) >> go(prev.updated(idx, range), tl)

        case Some(((idx, range), tl)) =>
          go(prev, tl)
      }
    }

    in => go(List.fill(3)(Double.MaxValue), in).stream
  }
}
