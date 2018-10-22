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

    def step(): RBN = {
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
  }

  /**
    * A simple perturbative function to alter an arbitrary RBN by
    * giving nodes a new state given by folding its neighbors over
    * XOR.
    */
  def XOR(neighbours: List[Boolean]): Boolean = {
    neighbours.foldLeft(false)(_ ^ _)
  }
}
