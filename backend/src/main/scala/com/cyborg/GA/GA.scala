package com.cyborg

import fs2._
import fs2.Stream._

/**
  Responsible for trying different neural networks as well as handling the
  unpleasentries dealing with queues etc.

  Currently not really a GA, just a mockup for the sake of API and some results
  */
object ffGA {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  import Filters._
  // import Filters.FeedForward._
  import seqUtils._
  import genetics._

  import params.GA._

  // Generates a new set of neural networks, no guarantees that they'll be any good...
  def generate(seed: ScoredSeq[FeedForward]): List[FeedForward] = {
    // println("generate pipes")
    val freak = mutate(seed.randomSample(1).repr.head._2)
    val rouletteScaled = seed.sort.rouletteScale
    val selectedParents = seed.randomSample(2)
    val (child1, child2) = fugg(selectedParents.repr.head._2, selectedParents.repr.tail.head._2)
    val newz = Vector(freak, child1, child2)
    val oldz = rouletteScaled.strip.takeRight(pipesKeptPerGeneration)
    (newz ++ oldz).toList
  }

  // A pipe that takes in nets + evals and returns new pipes using a genetic algo
  // Keeps track of networks for us, ensuring we wont have to do anything other than passing
  // the evaluations
  def experimentBatchPipe[F[_]](layout: List[Int]): Pipe[F,Double, Pipe[F,ffANNinput,ffANNoutput]] = {

    def go(previous: List[FeedForward], s: Stream[F,Double]): Pull[F,Pipe[F,ffANNinput,ffANNoutput],Unit] =
      s.pull.unconsN(pipesPerGeneration,false) flatMap {
        case Some((segment,tl)) => {
            // val folded = chunks.foldLeft(List[Double]())(_++_.toList).zip(previous)
          val folded = segment.toList.zip(previous)
          val asScored = ScoredSeq(folded.toVector)
          val nextPop = generate(asScored)
          val nextPipes = nextPop.map(ANNPipes.ffPipe[F](_))
          Pull.output(Chunk.seq(nextPipes)) >> go(nextPop, tl)
        }
        case None => Pull.done
      }


    def init(init: List[FeedForward], s: Stream[F,Double]): Pull[F,Pipe[F,ffANNinput,ffANNoutput],Unit] =
      Pull.output(Chunk.seq(init.map(ANNPipes.ffPipe[F](_)))) >> go(init, s)

    val initNetworks =
      (0 until pipesPerGeneration).map(_ => (Filters.FeedForward.randomNetWithLayout(layout))).toList

    in => init(initNetworks, in).stream
  }
}


/**
  Should implement some of the basic stuff often used for ranking populations etc in GAs
  */
object seqUtils {
  import scala.util.Random

  type ScaledBy

  case class ScoredSeq[A](repr: Vector[(Double,A)]) {
    def scoreSum: Double = (.0 /: repr)( (sum, tup) => sum + tup._1)
    def sort = copy(repr.sortWith(_._1 < _._1))

    def normalize = copy(repr.map(λ => (λ._1/scoreSum, λ._2)))
    def scale(f: (Double,Double) => Double) = copy(repr.map(λ => (f(scoreSum,λ._1),λ._2)))

    def strip: Vector[A] = repr.map(_._2)

    /**
      Requires the list to be sorted, looking into ways to encode this
      */
    def rouletteScale = copy(
      ((.0,List[(Double,A)]()) /: repr)((acc,λ) =>
        {
          val nextScore = acc._1 + λ._1
          val nextElement = (nextScore, λ._2)
          (nextScore, acc._2 :+ nextElement)
        })._2.toVector)

    def randomSample(samples: Int) = {
      val indexes = Random.shuffle(0 to repr.length - 1).take(samples)
      copy(indexes.map(λ => repr(λ)).toVector)
    }

    /**
      Also known as roulette.
      Iterates through a list, picks the element if the numbers up
      */
    def biasedSample(samples: Int): Vector[A] = {

      // Selections cannot contain elements with a value higher than the highest scoring individual
      val selections = Vector.fill(samples)(Random.nextDouble).sorted.map(_*repr.last._1)
      println(selections)

      def hepler(targets: Vector[Double], animals: Vector[(Double,A)]): Vector[A] =
        targets match {
          case h +: tail => {
            val (a, remainingAs) = hapler(h,animals)
            a +: hepler(tail, remainingAs)
          }
          case _ => Vector.empty
        }

        def hapler(target: Double, animals: Vector[(Double,A)]): (A, Vector[(Double,A)]) = {
          val memes = animals.dropWhile(λ => (target > λ._1))
          (memes.head._2, memes)
        }

      hepler(selections, repr)

    }
  }
}
