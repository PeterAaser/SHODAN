package cyborg


import cats.effect.IO
import cats.effect._
import cats.data.Kleisli
import cats._

import scala.concurrent.duration._

import cyborg.Settings._
import utilz._
import fs2._
// import params._
import bonus._
import params.game._

// Defines the relation between sensory input and perturbation frequency
sealed trait PerturbationTransform
case object Linear     extends PerturbationTransform
case object Binary     extends PerturbationTransform
// case object ExpFalloff extends PerturbationTransform


/**
  * Contains the filters that transform sensory input into perturbation requests.
  */
class PerturbationTransforms(conf: FullSettings) {

  val maxFreq = hardcode(10.0)
  val minFreq = hardcode(0.33)


  /**
    * Normalizes a sightrange in accordance to minimum and maximum sight ranges.
    * Values that fall outside of the maximum sightrange will become 0.0
    * values that are inside will be truncated to the maximum of 1.0
    */
  def normalize(range: Double): Double = {
    val actualRange = sightRange - deadZone
    val a = 1.0/(deadZone - sightRange)
    val b = 1.0 - a*deadZone

    if(range < deadZone)
      1.0
    else if(range > sightRange)
      0.0
    else
      a*range + b
  }


  def binary(range: Double): Double = {
    if(range > sightRange)
      1.0
    else
      0.0
  }



  def scalarToPeriod(scalar: Double): Option[FiniteDuration] = {
    if(!scalar.isInRange(0.0, 1.0))
      None
    else {
      val freq = (scalar*maxFreq) + ((1.0 - scalar)*minFreq)
      Some((1.0/freq).seconds)
    }
  }

  def defaultTransform(range: Double): Option[FiniteDuration] =
    scalarToPeriod(normalize(range))


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
  def toStimReq[F[_]]: Pipe[F,(Int, Double), (Int, Option[FiniteDuration])] = {

    val dummy: PerturbationTransform = Binary
    val transform: Double => Option[FiniteDuration] = dummy match {
      case Linear => normalize _ andThen scalarToPeriod
      case Binary => binary _ andThen scalarToPeriod
      // case ExpFalloff => normalize _ andThen scalarToPeriod
    }

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
