package cyborg

import cats.effect._
import fs2._
import fs2.Stream._
import fs2.concurrent.Queue
import wallAvoid._
import utilz._
import FFANN._
import Genetics._
import scala.util.Random

/**
  Responsible for trying different neural networks as well as handling the
  unpleasentries dealing with queues etc.
  Currently not really a GA, just a mockup for the sake of API and some results
  */
class GArunner[F[_]](gaSettings: Setting.GAsettings, filterSettings: Setting.ReadoutSettings) {

  import GAChunkUtils._
  import gaSettings._
  import filterSettings._

  val challengesPerPipe = 5

  def FFANNGenerator[F[_]]: Pipe[F,Double,FeedForward] = {

    def init(s: Stream[F,Double]): Pull[F,FeedForward,Unit] = {
      val initNetworks = (0 until pipesPerGeneration)
        .map(_ => (FFANN.randomNet(filterSettings)))

      Pull.output(Chunk.seq(initNetworks)) >>
        go(Chunk.seq(initNetworks), s)
    }


    def go(previous: Chunk[FeedForward], evals: Stream[F, Double]): Pull[F,FeedForward,Unit] = {
      evals.pull.unconsN((pipesPerGeneration - 1), false) flatMap {
        case Some((chunk, tl)) => {
          say("got evaluations")
          val scoredPipes = ScoredChunk(chunk.zip(previous))
          val nextPop = Chunk.seq(generate(scoredPipes))
            Pull.output(nextPop) >> go(nextPop,tl)
        }
        case None => {
          say("uh oh")
          Pull.done
        }
      }
    }


    def generate(seed: ScoredChunk[FeedForward]): List[FeedForward] = {
      say("Generated pipes")
      val mutated = mutate(seed.randomSample(1).repr.head.get._2)
      val rouletteScaled = seed.sort.rouletteScale
      val selectedParents = seed.randomSample(2)
      val (child1, child2) = fugg(selectedParents.repr.head.get._2, selectedParents.repr.drop(1).head.get._2)
      val newz = Chunk(mutated, child1, child2)

      // lol no takeRight
      // val oldz = rouletteScaled.strip.takeRight(pipesKeptPerGeneration)
      val oldz = rouletteScaled.strip.drop(rouletteScaled.repr.size - pipesKeptPerGeneration)

      (newz ++ oldz).toList
    }

    in => init(in).stream
  }


  object GAChunkUtils {

    case class ScoredChunk[A](repr: Chunk[(Double,A)]) {
      def scoreSum: Double = repr.foldLeft(.0)( (sum, tup) => sum + tup._1)
      def sort = copy(Chunk.seq(repr.toList.sortWith(_._1 < _._1)))

      def normalize = copy(repr.map(λ => (λ._1/scoreSum, λ._2)))
      def scale(f: (Double,Double) => Double) = copy(repr.map(λ => (f(scoreSum,λ._1),λ._2)))

      def strip: Chunk[A] = repr.map(_._2)

      /**
        Requires the list to be sorted, looking into ways to encode this
        */
      def rouletteScale = copy(
        Chunk.seq(
          repr.foldLeft((.0,List[(Double,A)]()))((acc, λ) =>
            {
              val nextScore = acc._1 + λ._1
              val nextElement = (nextScore, λ._2)
              (nextScore, acc._2 :+ nextElement)
            })._2)
      )

      def randomSample(samples: Int) = {
        val indexes = Random.shuffle(0 to repr.size - 1).take(samples)
        copy(Chunk.seq(indexes.map(λ => repr(λ))))
      }

      /**
        Also known as roulette.
        Iterates through a list, picks the element if the numbers up
        */
      def biasedSample(samples: Int): Chunk[A] = {

        val repr_ = repr.toVector
        // Selections cannot contain elements with a value higher than the highest scoring individual
        val selections = Vector.fill(samples)(Random.nextDouble).sorted.map(_*repr_.last._1)
        say(selections)

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

        Chunk.seq(hepler(selections, repr_))

      }
    }
  }
}
