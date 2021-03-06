package cyborg

import fs2.Chunk
import scala.language.higherKinds
import scala.util.Random
import utilz._
import bonus._
import fs2._

import scala.util.hashing.MurmurHash3

object FFANN {

  type Activator = Double => Double
  val linear:  Activator = x => x

  /**
    * With sigmoid it is necessary to have two outputs since the range is (0.0, 1.0)
    * We want the dude to be able to turn both ways..
    */
  val sigmoid: Activator = x => (1.0/(1.0 + epow(-x))) 

  /**
    * layout is a list specifying layer sizes. For a single layer net just pass a one-element list.
    * The weights list also contains biases. Since these are not constructed manually it doesn't matter much.
    */
  case class FeedForward(layout: List[Int], weights: List[Double], activator: Activator = sigmoid) {

    val layers = layout.length
    val layout_ = layout.toArray

    /**
      * Ugly, but that's OK
      */
    val weightArray: Array[Array[Double]] = Array.ofDim(layers - 1)
    val nodeArray: Array[Array[Double]]   = Array.ofDim(layers)

    def reset: Unit = for(ii <- 0 until nodeArray.size)
      for(kk <- 0 until nodeArray(ii).size)
        nodeArray(ii)(kk) = 0.0

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

    def internalState: String = {
      nodeArray.foldLeft(""){ case (acc, nodes) =>
        acc + nodes.map(ii => "[%.2f]".format(ii)).mkString + "\n"
      }
    }

    def drawWeights: String = {
      weightArray.foldLeft(""){ case (acc, weights) =>
        acc + weights.map(ii => "[%.2f]".format(ii)).mkString + "\n"
      }
    }

    /**
      Runs the network.
      This does change the networks internal state, however none of this is visible
      preserving referential transparency.
      */
    def runNet(input: Chunk[Double]): Chunk[Double] = {

      nodeArray(0) = input.toArray
      for(ii <- 1 until layers) {
        calculateLayer(ii)
      }

      val res = Chunk.array(nodeArray(layers - 1).clone)
      reset
      res
    }

    def feed(input: Chunk[Double]): Chunk[Double] = runNet(input)

    def toPipe[F[_]] = ffPipe[F](this)

    override def toString: String = name

    def printNet: String = {
      weights.map(x => "[%.2f]".format(x)).mkString("")
    }

    def name: String = {
      import cyborg.bonus._
      val hash = MurmurHash3.listHash(weights, 0)
      Greeks(hash %% 10) + " " +
      Greeks(hash * 31 %% 10) + " " +
      Greeks(hash * 13 %% 10) + " " +
      Greeks(hash * 3  %% 10)
    }
  }


  def randomNet(configuration: Settings.ReadoutSettings): FeedForward = {

    /**
      Prepend the amount of inputs to the network, and append the two outputs.
      This means that the empty list is a valid network as it ends up being a perceptron
      (well, two perceptrons, whatever)
      */
    val layout = configuration.getLayout
    val neededBias = layout.tail.sum
    val neededWeights = ((layout zip layout.tail).map{ case (a, b) => {a*b}}.sum)

    val weights = (0 until (neededBias + neededWeights)).map(_ => -0.5 + (Random.nextDouble()))

    FeedForward(layout, weights.toList)
  }


  def ffPipe[F[_]](net: FeedForward): Pipe[F,Chunk[Double],Chunk[Double]] = {

    // uncomment to get pipe names like Alpha Iota Delta Sigma
    // say(s"creating pipe from net $net")

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

  val Greeks = Map(
    0 -> "Alpha",
    1 -> "Beta",
    2 -> "Gamma",
    3 -> "Delta",
    4 -> "Epsilon",
    5 -> "Zeta",
    6 -> "Eta",
    7 -> "Sigma", // sorry Theta, I did it all for the memes
    8 -> "Iota",
    9 -> "Kappa",
    10 -> "Lambda"
  )

  val huh = MurmurHash3.orderedHash(List(1.0,2.0))
}
