package cyborg

import scala.concurrent.duration._
import fs2._
import fs2.concurrent.Signal
import cats.effect._
import backendImplicits._
import RBN._
import RBNStreams._


object ReservoirUtils {

}


object RBNUtils {
  /**
    * Converts stimuli in the form of pulses, given as a
    * FiniteDuration for the period, into perturbations for an RBN of
    * size equal to the amount of channels of the stimuli. This does
    * not yet support a pull based fs2 implementation, but can be used
    * for such an implementation.
    */
  def stimuliToPerturbation(stimuli: List[(Int, Option[FiniteDuration])])
      : List[Node] = {
    // For now there is no continuous way of perturbing the RBN -- just
    // flip the boolean value if its channel has a sufficiently strong
    // signal.
    stimuli.flatMap{case (channel, duration) => {
      duration match {
        case None => None
        case Some(d) => if (d < 0.2.second) None else Some(channel)
      }
    }}
  }


  /**
    * Continuously output an RBN, changing as its signal is updated
    * from elsewhere.
    */
  def outputRBNNodeState[F[_]: Concurrent : Timer](rbn: RBN, node: Node, samplerate: Int,
    resolution: FiniteDuration = 0.05.second, throttle: Boolean = true)
      : Stream[F, Int] = {
    rbn.outputNodeState(node, samplerate, throttle = throttle).take(samplerate) ++
    outputRBNNodeState(rbn.step, node, samplerate, resolution, throttle)
  }
}
