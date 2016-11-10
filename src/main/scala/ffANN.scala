package SHODAN

// todo make generic and maybe also not suck
object Filters {
  case class FeedForward[T](layout: List[Int], bias: List[T], weights: List[T])
                        (implicit ev: Numeric[T]) {

    import ev._

    import FeedForward._

    val activator: T => T = λ => λ

    val layers = layout.length

    val layerSlicePoints =
      layout
        .zip(layout.tail)
        .map(λ => λ._1 * λ._2)


    val biasSlices: List[List[T]] =
      sliceVector(layout.tail, bias)


    val matrixList: List[List[List[T]]] = {
      val slices = sliceVector(layerSlicePoints, weights)
                              (layout zip slices).map(λ => toMatrix(λ._1, λ._2))
    }.map(_.transpose)


    val layerCalculations: List[ List[T] => List[T] ] =
      (0 until layout.length - 1).toList.map(
        idx => {
          ((input: List[T]) => calculateLayerOutput(biasSlices(idx), matrixList(idx), input)
                  .map(activator(_)))
      })


    val feed: List[T] => List[T] = input =>
      (input /: layerCalculations)((λ, f) => f(λ))

  }
  case object FeedForward {

    def sliceVector[T: Numeric](points: List[Int], victim: List[T]): List[List[T]] =
      (points, victim) match {
        case ((p :: ps), _) => victim.take(p) :: sliceVector(ps, victim.drop(p))
        case _ => List[List[T]]()
      }

    def toMatrix[T: Numeric](inputs: Int, weights: List[T]): List[List[T]] = {
      val outputs = weights.length/inputs
      weights.sliding(outputs, outputs).toList
    }

    def calculateLayerOutput[T](
        bias: List[T]
      , weights: List[List[T]]
      , input: List[T]
    )(implicit ev: Numeric[T]

    ): List[T] = {

      import ev._

      val outputs = weights.map(λ => (λ zip input).map(λ => λ._1 * λ._2).sum)
      (outputs zip bias).map(λ => λ._1 + λ._2)

    }
  }
}
