package cyborg


import cats.effect.IO
import cats.effect._


import HttpClient._
import DspRegisters._
import scala.util.Random
import twiddle._

object DspComms {

  import twiddle._

  import fs2._
  def stimuliRequestSink(throttle: Int = 1000): Sink[IO,List[Double]] = {

    def stimuliRequest(vision: List[Double]): IO[Unit] = {

      import MEAMEutilz._
      def toStimFrequency(transform: Double => Double, distance: Double): Double = {
        setDomain(transform)(distance)
      }

      val desc2 = vision.map(toStimFrequency( logScaleBuilder(scala.math.E), _)).foldLeft(0.0)(_+_)

      val period = if(desc2 > 0) 10000 else 100000

      for {
        write <- simpleStimRequest(SimpleStimReq(period))
      } yield {
        println(s"sent stim req $period")
      }
    }
    _.through(utilz.mapN(throttle, _.toArray.head)).evalMap(stimuliRequest)
  }
}
