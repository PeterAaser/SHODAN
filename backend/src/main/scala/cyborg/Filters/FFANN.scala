package cyborg

import fs2.Chunk
import scala.language.higherKinds
import scala.util.Random
import utilz._
import fs2._

object FFANN {

  val defaultActivator: Double => Double = x => x

  // Unlike the previous version bias is not a separate list
  case class FeedForward(layout: List[Int], weights: List[Double], activator: Double => Double = defaultActivator) {

    val layers = layout.length
    val layout_ = layout.toArray

    /**
      Ugly, but that's OK
      */
    val weightArray: Array[Array[Double]] = Array.ofDim(layers - 1)
    val nodeArray: Array[Array[Double]]   = Array.ofDim(layers)

    /**
      Assign weights and bias to the weight array
      */
    var weightsUsed = 0
    (layout zip layout.tail).map(x => (x._1*x._2) + x._2).zipWithIndex.foreach { case(ws, idx) =>
      weightArray(idx) = weights.drop(weightsUsed).take(ws).toArray
      weightsUsed += ws
    }
    layout.zipWithIndex.foreach { case(nodes,idx) => nodeArray(idx) = Array.ofDim(nodes) }


    /**
      Updates the output nodes and runs their activator
      */
    def calculateLayer(idx: Int): Unit = {
      val ins  = layout_(idx - 1)
      val outs = layout_(idx)

      val old  = idx - 1
      val next = idx

      var wIdx = 0
      for(ii <- 0 until ins){
        for(kk <- 0 until outs){
          nodeArray(next)(kk) += nodeArray(old)(ii) * weightArray(old)(wIdx)
          wIdx += 1
        }
      }
      for(ii <- 0 until outs){
        nodeArray(next)(ii) += weightArray(old)(wIdx)
        nodeArray(next)(ii) = activator(nodeArray(next)(ii))
        wIdx += 1
      }
    }


    /**
      Runs the network.
      This does change the networks internal state, however none of this is visible
      preserving referential transparency.

      not tested lol
      */
    def runNet(input: Chunk[Double]): Chunk[Double] = {

      nodeArray(0) = input.toArray
      for(ii <- 1 until layers) {
        calculateLayer(ii)
      }

      Chunk.array(nodeArray(layers - 1))
    }

    def toPipe[F[_]] = ffPipe[F](this)

  }


  def randomNet(configuration: Settings.ReadoutSettings): FeedForward = {

    import configuration._

    /**
      Prepend the amount of inputs to the network, and append the two outputs.
      This means that the empty list is a valid network as it ends up being a perceptron
      (well, two perceptrons, whatever)
      */
    val layout = configuration.layout
    say(layout)
    val neededBias = layout.tail.sum
    val neededWeights = ((layout zip layout.tail).map{ case (a, b) => {a*b}}.sum)

    val weights = (0 until (neededBias + neededWeights)).map(_ => (Random.nextDouble()))

    FeedForward(layout, weights.toList)
  }


  /**
    same as ffPipe, but with chunks to make the transition easier.
    Should eventually replace ffPipe
    */
  def ffPipe[F[_]](net: FeedForward): Pipe[F,Chunk[Double],Chunk[Double]] = {

    def go(s: Stream[F, Chunk[Double]]): Pull[F,Chunk[Double],Unit] = {
      s.pull.uncons1 flatMap {
        case Some((chunk, tl)) => {
          val outputs = net.runNet(chunk)
          Pull.output1(outputs) >> go(tl)
        }
        case None => Pull.done
      }
    }
    in => go(in).stream

  }
}
