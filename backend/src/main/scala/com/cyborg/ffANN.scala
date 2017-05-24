package com.cyborg

import scala.language.higherKinds
import scala.util.Random

object Filters {
  case class FeedForward[T](layout: List[Int], bias: List[T], weights: List[T])
                        (implicit ev: Numeric[T]) {

    import FeedForward._
    require((layout.size > 1), "No point making a single layer ANN")

    val neededWeights = ((layout zip layout.tail).map{ case (a, b) => {a*b}}.sum)
    if(neededWeights != weights.length){
      println(" ffANN error ")
    }
    require(
      neededWeights == weights.length,
      s"incorrect amount of weights. Needed: $neededWeights, Provided: ${weights.length}"
    )
    val neededBias = layout.tail.sum
    if(neededBias != bias.length){
      println(" ffANN error ")
    }
    require(
      neededBias == bias.length,
      s"incorrect amount of bias weights. Needed: $neededBias, Provided: ${bias.length}"
    )



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
  object FeedForward {

    type Layout = List[Int]


    def randomNetWithLayout(layout: Layout): FeedForward[Double] = {
      println("Created random net with layout")

      // hardcoded
      val weightMin = -2.0
      val weightMax =  2.0

      val neededBias = layout.tail.sum
      val neededWeights = ((layout zip layout.tail).map{ case (a, b) => {a*b}}.sum)

      val bias = (0 until neededBias).map(_ => (Random.nextDouble() * (weightMax - weightMin)) - weightMin).toList
      val weights = (0 until neededWeights).map(_ => (Random.nextDouble() * (weightMax - weightMin)) - weightMin).toList

      FeedForward[Double](layout, bias, weights)

    }

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

  object ANNPipes {

    import fs2._

    def ffPipe[F[_]](net: FeedForward[Double]): Pipe[F,Vector[Double], List[Double]] = {

      def go: Handle[F,Vector[Double]] => Pull[F,List[Double],Unit] = h => {
        h.await1 flatMap {
          case (chunk, h) => {
            val outputs = net.feed(chunk.toList)
            // println(s"outputs from ANN was $outputs")
            Pull.output1(outputs) >> go(h)
          }
        }
      }
      _.pull(go)
    }
  }
}
