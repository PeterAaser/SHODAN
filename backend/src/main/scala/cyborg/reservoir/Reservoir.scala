package cyborg

object RBNContext {
  type State = List[Boolean]
  type Node  = Int
  type Edges = List[List[Node]]
  type Rule  = List[Boolean] => Boolean

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
    def perturb(perturbations: List[(Node, Boolean)]): RBN = {
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
}
