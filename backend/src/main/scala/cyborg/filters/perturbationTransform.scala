package cyborg


import cats.effect.IO
import cats.effect._

import MEAMEutilz._
import scala.concurrent.duration._
import utilz._
import fs2._
import params._


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


  /** Transforms agent sight ranges to stimuli pulses. The default
    * transform is a linear transform.
    */
  def toStimReq[F[_]](transform: Double => Option[FiniteDuration] = linearTransform)
      : Pipe[F,List[Double], List[(Int, Option[FiniteDuration])]] = {

    def go(s: Stream[F, List[Double]])
        : Pull[F, List[(Int, Option[FiniteDuration])], Unit] = {
      s.pull.uncons.flatMap {
        case Some((sr,tl)) =>
          Pull.output1(sr.force.toList.head.zipWithIndex.map(λ => (λ._2, transform(λ._1)))) >> go(tl)
        case None => Pull.done
      }
    }

    in => go(in).stream
  }


  def stimuliRequestSink(tolerance: Double)(implicit ec: EC): Sink[IO,List[Double]] = {

    def go(prev: List[Double], s: Stream[IO,List[Double]]): Pull[IO,IO[Unit],Unit] = {
      s.pull.uncons1 flatMap {
        case Some((seg,tl)) => {

          val clamped = seg.map(λ => if(λ > 3200.0) 3200.0 else( if(λ < 200.0) 200.0 else λ))
          val shouldUpdate = {

            val diffExceedsThreshHold = ((prev zip clamped).map { case(old,next) => math.abs(old-next) })
              .foldLeft(false){ (a,b) => a || (b >= tolerance) }

            val sensorOutOfRange = (prev zip clamped).map {
              case(old,next) => ((next >= maxDistance) && !(old >= maxDistance))
            }.foldLeft(false)(_||_)
            (diffExceedsThreshHold || sensorOutOfRange)
          }

          // val stimReq = DspCalls.createStimRequests(clamped)
          val stimReq = IO {say("Firing off an unimplemented stim request, CLOG WARNING")}

          if(shouldUpdate) {
            Pull.output1(stimReq) >> go(clamped,tl)
          }
          else
            go(prev,tl)
        }
        case None => Pull.done
      }
    }


    def init(s: Stream[IO,List[Double]]): Pull[IO,IO[Unit],Unit] = {
      s.pull.uncons1 flatMap {
        case Some((seg,tl)) => {
          go(seg, tl)
        }
        case None => {
          Pull.done
        }
      }
    }

    in => init(in).stream.flatMap(Stream.eval).drain.join(100)
  }
}
