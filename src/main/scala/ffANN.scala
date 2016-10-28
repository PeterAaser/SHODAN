package SHODAN

// todo make generic and maybe also not suck
object Filters {
  case class FeedForward(layout: List[Int], bias: List[Int], weights: List[Int]) {

    import FeedForward._

    val activator: Int => Int = λ => λ

    val layers = layout.length

    val layerSlicePoints =
      layout
        .zip(layout.tail)
        .map(λ => λ._1 * λ._2)


    val biasSlices: List[List[Int]] =
      sliceVector(layout.tail, bias)


    val matrixList: List[List[List[Int]]] = {
      val slices = sliceVector(layerSlicePoints, weights)
                              (layout zip slices).map(λ => toMatrix(λ._1, λ._2))
    }.map(_.transpose)


    val layerCalculations: List[ List[Int] => List[Int] ] =
      (0 until layout.length - 1).toList.map(
        idx => {
          ((input: List[Int]) => calculateLayerOutput(biasSlices(idx), matrixList(idx), input)
                  .map(activator(_)))
      })


    val feed: List[Int] => List[Int] = input =>
      (input /: layerCalculations)((λ, f) => f(λ))

  }
  case object FeedForward {

    def sliceVector(points: List[Int], victim: List[Int]): List[List[Int]] =
      (points, victim) match {
        case ((p :: ps), _) => victim.take(p) :: sliceVector(ps, victim.drop(p))
        case _ => List[List[Int]]()
      }

    def toMatrix(inputs: Int, weights: List[Int]): List[List[Int]] = {
      val outputs = weights.length/inputs
      weights.sliding(outputs, outputs).toList
    }

    def calculateLayerOutput(
        bias: List[Int]
      , weights: List[List[Int]]
      , input: List[Int]

    ): List[Int] = {

      val outputs = weights.map(λ => (λ zip input).map(λ => λ._1 * λ._2).sum)
      (outputs zip bias).map(λ => λ._1 + λ._2)

    }
  }
}
