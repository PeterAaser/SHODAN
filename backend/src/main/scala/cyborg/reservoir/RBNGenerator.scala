package cyborg

import cyborg.RBN._

object RBNGen {
  object ActiveRBNs {
    val randomRBN = createRandomRBN(60, 3, 0.5)
  }


  // Retired for now -- maybe useful later?
  object RBNFunctions {
    /**
      * A simple rule to update an arbitrary RBN by giving nodes a new
      * state given by folding its neighbors over XOR.
      */
    def XOR(neighbours: List[Boolean]): Boolean = {
      neighbours.foldLeft(false)(_ ^ _)
    }
  }


  def generateRule(connectivity: Int, p: Double): Rule = {
    List.fill(math.pow(2, connectivity).toInt)(math.random < 0.5)
  }


  def generateRules(numNodes: Int, connectivity: Int, p: Double)
      : List[Rule] = {
    List.fill(numNodes)(generateRule(connectivity, p))
  }


  def createRandomRBN(numNodes: Int, connectivity: Int, p: Double): RBN = {
    import scala.util.Random

    // (TODO): Use proper functional RNG library
    val nodes = (0 until numNodes).toList
    val state = List.fill(numNodes)(math.random < 0.5)
    val rules = generateRules(numNodes, connectivity, p)
    val edges = nodes.map{i =>
      Random.shuffle(nodes.filter(n => n != i)).take(connectivity).toList
    }

    RBN(state, edges, rules)
  }
}
