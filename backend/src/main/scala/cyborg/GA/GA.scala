package cyborg

import cats.effect.IO
import cats.effect._
import fs2._
import fs2.Stream._
import wallAvoid._
import utilz._
import Filters._
import genetics._
import seqUtils._

/**
  Responsible for trying different neural networks as well as handling the
  unpleasentries dealing with queues etc.

  Currently not really a GA, just a mockup for the sake of API and some results
  */
object GArunner {

  import params.GA._
  import params.filtering._

  type ReservoirOutput = Vector[Double]
  type FilterOutput    = List[Double]
  type O               = Agent


  /**
    Sets up a simulation running an agent in 5 different initial poses
    aka challenges.
    */
  def createSimRunner(implicit ec: EC): () => Pipe[IO, FilterOutput, O] = {

    // say("creating simrunner")
    def simRunner(agent: Agent): Pipe[IO,FilterOutput, Agent] = {
      def go(ticks: Int, agent: Agent, s: Stream[IO,FilterOutput]): Pull[IO,Agent,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((agentInput, tl)) => {
            val nextAgent = Agent.updateAgent(agent, agentInput)
            if (ticks > 0){
              Pull.output1(nextAgent) >> go(ticks - 1, nextAgent, tl)
            }
            else {
              Pull.output1(nextAgent) >> Pull.done
            }
          }
          case _ => Pull.done
        }
      }
      in => go(ticksPerEval, agent, in).stream
    }

    val challenges: List[Agent] = createChallenges
    val challengePipes: List[Pipe[IO, FilterOutput, O]] = challenges.map(simRunner(_))
    def challengePipe: Pipe[IO,FilterOutput, Agent] = Pipe.join(Stream.emits(challengePipes).covary[IO])

    () => challengePipe
  }


  /**
    This is the genetic algorithm. As per spec it must emit a default, initial element.
    Internally it keeps a store of pipe generators (one generation).
    */
  def filterGenerator: Pipe[IO, Double, Pipe[IO, ReservoirOutput, Option[FilterOutput]]] = {

    def init(s: Stream[IO,Double]): Pull[IO, Pipe[IO, ReservoirOutput, Option[FilterOutput]], Unit] = {
      val initNetworks = (0 until pipesPerGeneration)
        .map(_ => (Filters.FeedForward.randomNetWithLayout(layout)))
        .toList

      // say(s"Outputting $pipesPerGeneration pipes")
      Pull.output(Chunk.seq(initNetworks.map(ANNPipes.ffPipeO[IO](ticksPerEval*5, _))).toSegment) >> go(initNetworks, s)
    }


    def go(previous: List[FeedForward], evals: Stream[IO, Double]): Pull[IO, Pipe[IO, ReservoirOutput, Option[FilterOutput]], Unit] = {
      evals.pull.unconsN((pipesPerGeneration - 1).toLong, false) flatMap {
        case Some((segment, tl)) => {
          // say("got evaluations")
          val scoredPipes = ScoredSeq(segment.force.toList.zip(previous).toVector)
          val nextPop = generate(scoredPipes)
          val nextPipes = nextPop.map(ANNPipes.ffPipeO[IO](ticksPerEval*5, _))
          Pull.output(Chunk.seq(nextPipes).toSegment) >> go(nextPop,tl)
        }
        case None => {
          // say("uh oh")
          Pull.done
        }
      }
    }

    // Generates a new set of neural networks, no guarantees that they'll be any good...
    def generate(seed: ScoredSeq[FeedForward]): List[FeedForward] = {
      // say("Generated pipes")
      val freak = mutate(seed.randomSample(1).repr.head._2)
      val rouletteScaled = seed.sort.rouletteScale
      val selectedParents = seed.randomSample(2)
      val (child1, child2) = fugg(selectedParents.repr.head._2, selectedParents.repr.tail.head._2)
      val newz = Vector(freak, child1, child2)
      val oldz = rouletteScaled.strip.takeRight(pipesKeptPerGeneration)

      (newz ++ oldz).toList
    }

    in => init(in).stream
  }


  /**
    A pipe which evaluates the performance of an agent
    */
  def evaluator: Pipe[IO,Agent,Double] = {
    def go(s: Stream[IO,Agent]): Pull[IO,Double,Unit] = {
      s.pull.unconsN(ticksPerEval.toLong, false) flatMap {
        case Some((seg, _)) => {
          // say("Evaluated a performance")
          val closest = seg.force.toList
            .map(_.distanceToClosest)
            .min

          Pull.output1(closest) >> Pull.done
        }
        case _ => {
          Pull.done
        }
      }
    }
    in => go(in).stream
  }


  def gaPipe(implicit ec: EC) = Feedback.experimentPipe(createSimRunner, evaluator, filterGenerator)

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
      // say(selections)

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
