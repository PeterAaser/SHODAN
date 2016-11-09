package SHODAN

object boolanNetworks {

  case class RBN(
    state: List[Boolean]
    , connectivity: List[List[Int]]
    , rules: List[List[Boolean]]){

    def update: RBN = {
      copy(state = (0 to state.length).toList.map{ index =>

        val connections: List[Int] = connectivity(index)
        val key: Int = connections.zipWithIndex.map { case (element, index) => {
          if(state(element)) 1 >> index - 1 else 0 }
        }.sum
        rules(index)(key)
      })
    }
  }

  def runNet(net: RBN): (List[Boolean], RBN) = {
    val next = net.update
    (next.state, next)
  }
}
