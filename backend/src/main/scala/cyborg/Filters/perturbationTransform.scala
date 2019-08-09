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
    if(range < sightRange)
      1.0
    else
      -1.0
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
    * 
    * TODO threshold should be parameter
    */
  def toStimReq[F[_]]: Pipe[F, (Int, Double), StimReq] = {

    val transform: Double => Option[FiniteDuration] = conf.perturbation.perturbationTransform match {
      case Linear => x => scalarToPeriod(normalize(x))
      case Binary => x => scalarToPeriod(binary(x))
      // case ExpFalloff => normalize _ andThen scalarToPeriod
    }

    def go(prev: List[Double], s: Stream[F, (Int, Double)]): Pull[F, StimReq, Unit] = {
      s.pull.uncons1.flatMap {
        case None => Pull.done
        case Some(((idx, range), tl)) if(rangeThreshExceeded(range, prev(idx))) => {
          val req = transform(range).map(d => SetPeriod(idx, d)).getOrElse(DisableStim(idx))
          Pull.output1(req) >> go(prev.updated(idx, range), tl)
        }

        case Some(((idx, range), tl)) => {
          go(prev, tl)
        }
      }
    }

    in => go(List.fill(3)(Double.MaxValue), in).stream
  }


  /**
    * Sometimes we must do as the gohpers do
    * 
    * The way this pipe is used currently is a problem, seeing as it keeps getting reset.
    * Not really sure where this happens, not that convenient to fix sadly, so for now
    * the awkward tristate logic remains
    */
  def toStimReqBinary[F[_]]: Pipe[F, (Int, Double), StimReq] = {

    def go(prev: List[Option[Boolean]], s: Stream[F, (Int, Double)]): Pull[F, StimReq, Unit] = {
      s.pull.uncons1.flatMap {
        case None => Pull.done

        // The case when an eye sees something for the first time
        case Some(((idx, range), tl)) if(!(prev(idx).isDefined)) => {
          if(range < params.game.sightRange){
            val req = SetPeriod(idx, scalarToPeriod(1.0).get)
            Pull.output1(req) >> go(prev.updated(idx, Some(true)), tl)
          }
          else {
            val req = DisableStim(idx)
            Pull.output1(req) >> go(prev.updated(idx, Some(false)), tl)
          }
        }


        // The case when an eye goes out of range when it was previously in range
        case Some(((idx, range), tl)) if((range < params.game.sightRange) && (!prev(idx).get)) => {
          if(idx == 0){
          }
          val req = SetPeriod(idx, scalarToPeriod(1.0).get)
          Pull.output1(req) >> go(prev.updated(idx, Some(true)), tl)
        }

        // The case when an eye goes into range when it was previously out of range
        case Some(((idx, range), tl)) if((range > params.game.sightRange) && (prev(idx).get)) => {
          val req = DisableStim(idx)
          Pull.output1(req) >> go(prev.updated(idx, Some(false)), tl)
        }

        // No others cases are interesting for binary
        case Some(((idx, range), tl)) => {
          go(prev, tl)
        }
      }
    }
     in => go(List.fill(3)(None), in).stream
  }
}
