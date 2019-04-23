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
    * Linearly transforms a sight-range to a scalar if below max sight-range
    * 
    * At or below min-range the value is 1.0, at max-range 0.0
    */
  def linearTransform(range: Double): Option[Double] = for {
    _ <- Option.when_(range <= game.sightRange)
  } yield {
    if(range <= game.deadZone)
      1.0
    else
      1.0 - range/game.sightRange
  }

  def scalarToPeriod(scalar: Double): FiniteDuration = {
    val freq = (scalar*experiment.maxFreq) + ((1.0 - scalar)*experiment.minFreq)
    (1.0/freq).seconds
  }

  def defaultTransform(range: Double): Option[FiniteDuration] =
    linearTransform(range).map(scalarToPeriod)


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
  def toStimReq[F[_]](transform: Double => Option[FiniteDuration] = defaultTransform)
      : Pipe[F,(Int, Double), (Int, Option[FiniteDuration])] = {

    def go(prev: List[Double], s: Stream[F, (Int, Double)])
        : Pull[F, (Int, Option[FiniteDuration]), Unit] = {
      s.pull.uncons1.flatMap {
        case None => Pull.done
        case Some(((idx, range), tl)) if(rangeThreshExceeded(range, prev(idx))) => {
          Pull.output1((idx, transform(range))) >> go(prev.updated(idx, range), tl)
        }

        case Some(((idx, range), tl)) =>
          go(prev, tl)
      }
    }

    in => go(List.fill(3)(Double.MaxValue), in).stream
  }
}
