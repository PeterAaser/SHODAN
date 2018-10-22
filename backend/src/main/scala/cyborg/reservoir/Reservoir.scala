package cyborg

import scala.concurrent.duration._
import params._

object RBNContext {
  type State         = List[Boolean]
  type Node          = Int
  type Edges         = List[List[Node]]
  type Perturbation  = (Node, Boolean)
  type Rule          = List[Boolean] => Boolean

  case class RBN(
    state: State,
    edges: Edges,
    rule:  Rule
  ) {
    /**
      * Neighbors are defined as the edges _into_ the node in
      * question, which may be confusing if thought of in CA terms.
      */
    def neighbors(node: Node): List[Boolean] = {
      edges(node).map(neighbor => state(neighbor))
    }

    def step: RBN = {
      copy(state = state.zipWithIndex.map{t =>
        rule(neighbors(t._2))
      })
    }

    /**
      * Apply a perturbation to the RBN, given as a list of nodes and
      * the new state.
      */
    def perturb(perturbations: List[Perturbation]): RBN = {
      copy(state = perturbations.foldLeft(state){(s, p) =>
        s.updated(p._1, p._2)
      })
    }

    /**
      * Find an attractor. Does not output the basin leading to the
      * given attractor, i.e. you'll only get the actual cycle.
      */
    def attractor(maxLength: Int): Option[List[State]] = {
      def go(seen: List[State], rbn: RBN, depth: Int): Option[List[State]] = {
        (seen, depth) match {
          case (_, 0) => None
          case (seen, _) if seen contains rbn.state =>
            Some(rbn.state +: seen.takeWhile(_ != rbn.state) ::: List(rbn.state))
          case _ => go(rbn.state +: seen, rbn.step, depth-1)
        }
      }

      go(List(), this, maxLength).map(_.reverse)
    }
  }

  /**
    * A simple rule to update an arbitrary RBN by giving nodes a new
    * state given by folding its neighbors over XOR.
    */
  def XOR(neighbours: List[Boolean]): Boolean = {
    neighbours.foldLeft(false)(_ ^ _)
  }

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
}
