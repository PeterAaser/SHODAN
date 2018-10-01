package cyborg


import cats.effect.IO
import cats.effect._

import MEAMEutilz._
import utilz._
import fs2._

object DspComms {


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
