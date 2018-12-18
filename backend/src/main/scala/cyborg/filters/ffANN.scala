package cyborg

import scala.language.higherKinds
import scala.util.Random
import utilz._

object Filters {

  case class FeedForward(layout: List[Int], bias: List[Double], weights: List[Double])
  {

    import FeedForward._
    // require((layout.size > 1), "No point making a single layer ANN. If you want a perceptron that's actually two layers")

    val neededWeights = ((layout zip layout.tail).map{ case (a, b) => {a*b}}.sum)
    if(neededWeights != weights.length){
      say(" ffANN error ")
    }
    require(
      neededWeights == weights.length,
      s"incorrect amount of weights. Needed: $neededWeights, Provided: ${weights.length}"
    )
    val neededBias = layout.tail.sum
    if(neededBias != bias.length){
      say(" ffANN error ")
    }
    require(
      neededBias == bias.length,
      s"incorrect amount of bias weights. Needed: $neededBias, Provided: ${bias.length}"
    )



    val activator: Double => Double = λ => λ

    val layers = layout.length

    val layerSlicePoints =
      layout
        .zip(layout.tail)
        .map(λ => λ._1 * λ._2)


    val biasSlices: List[List[Double]] =
      sliceVector(layout.tail, bias)


    val matrixList: List[List[List[Double]]] = {
      val slices = sliceVector(layerSlicePoints, weights)
                              (layout zip slices).map(λ => toMatrix(λ._1, λ._2))
    }.map(_.transpose)


    val layerCalculations: List[ List[Double] => List[Double] ] =
      (0 until layout.length - 1).toList.map(
        idx => {
          ((input: List[Double]) => calculateLayerOutput(biasSlices(idx), matrixList(idx), input)
                  .map(activator(_)))
      })

    val feed: List[Double] => List[Double] = input =>
      (input /: layerCalculations)((λ, f) => f(λ))

  }
  object FeedForward {

    type Layout = List[Int]

    def randomNetWithLayout(configuration: Setting.ReadoutSettings): FeedForward = {

      import configuration._

      /**
        Prepend the amount of inputs to the network, and append the two outputs.
        This means that the empty list is a valid network as it ends up being a perceptron
        (well, two perceptrons, whatever)
        */
      val layout = configuration.inputChannels.size :: configuration.ANNlayout ::: List(2)

      val neededBias = layout.tail.sum
      val neededWeights = ((layout zip layout.tail).map{ case (a, b) => {a*b}}.sum)

      val bias = (0 until neededBias).map(_ => (Random.nextDouble() * (weightMax - weightMin)) - weightMin).toList
      val weights = (0 until neededWeights).map(_ => (Random.nextDouble() * (weightMax - weightMin)) - weightMin).toList

      FeedForward(layout, bias, weights)
    }

    def sliceVector(points: List[Int], victim: List[Double]): List[List[Double]] =
      (points, victim) match {
        case ((p :: ps), _) => victim.take(p) :: sliceVector(ps, victim.drop(p))
        case _ => List[List[Double]]()
      }

    def toMatrix(inputs: Int, weights: List[Double]): List[List[Double]] = {
      val outputs = weights.length/inputs
      weights.sliding(outputs, outputs).toList
    }

    def calculateLayerOutput(
        bias: List[Double]
      , weights: List[List[Double]]
      , input: List[Double]
    ): List[Double] = {

      val outputs = weights.map(λ => (λ zip input).map(λ => λ._1 * λ._2).sum)
      (outputs zip bias).map(λ => λ._1 + λ._2)

    }
  }

  object ANNPipes {

    import fs2._

    def ffPipe[F[_]](net: FeedForward): Pipe[F,Vector[Double], List[Double]] = {

      def go(s: Stream[F, Vector[Double]]): Pull[F,List[Double],Unit] = {
        s.pull.uncons1 flatMap {
          case Some((seg, tl)) => {
            say("hello?")
            val outputs = net.feed(seg.toList)
            say("dunnit")
            Pull.output1(outputs) >> go(tl)
          }
          case None => Pull.done
        }
      }
      in => go(in).stream
    }


    // terrible hack
    def ffPipeO[F[_]](runs: Int, net: FeedForward): Pipe[F,Vector[Double], Option[List[Double]]] = {

      def go(run: Int, s: Stream[F, Vector[Double]]): Pull[F,Option[List[Double]],Unit] = {
        if(run == 0){
          Pull.output1(None)
        }
        else{
          s.pull.uncons1 flatMap {
            case Some((seg, tl)) => {
              val outputs = net.feed(seg.toList)
              Pull.output1(Some(outputs)) >> go(run - 1, tl)
            }
            case None => Pull.done
          }
        }
      }
      in => go(runs, in).stream
    }


    /**
      same as ffPipe, but with chunks to make the transition easier.
      Should eventually replace ffPipe
      */
    def ffPipeC[F[_]](net: FeedForward): Pipe[F,Chunk[Double],Chunk[Double]] = {

      def go(s: Stream[F, Chunk[Double]]): Pull[F,Chunk[Double],Unit] = {
        s.pull.uncons1 flatMap {
          case Some((chunk, tl)) => {
            val outputs = net.feed(chunk.toList)
            Pull.output1(Chunk.seq(outputs)) >> go(tl)
          }
          case None => Pull.done
        }
      }
      in => go(in).stream

    }
  }
}
