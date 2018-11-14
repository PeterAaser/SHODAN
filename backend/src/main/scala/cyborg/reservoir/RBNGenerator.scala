package cyborg

import cyborg.RBN._

object RBNGen {
  object ActiveRBNs {
    val randomRBN = createRandomRBN
  }


  object RBNFunctions {
    /**
      * A simple rule to update an arbitrary RBN by giving nodes a new
      * state given by folding its neighbors over XOR.
      */
    def XOR(neighbours: List[Boolean]): Boolean = {
      neighbours.foldLeft(false)(_ ^ _)
    }
  }


  def createRandomRBN: RBN = {
    import scala.util.Random

    // (TODO): Use proper functional RNG library
    val numNodes = 60
    val nodes = (0 until numNodes).toList
    val state = List.fill(numNodes)(math.random < 0.5)
    val edges = nodes.map{i =>
      Random.shuffle(nodes.filter(n => n != i)).take(2).toList
    }

    RBN(state, edges, RBNFunctions.XOR)
  }
}
