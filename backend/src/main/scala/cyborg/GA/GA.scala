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

  // TODO hardcoded
  val ticksPerEval = 1000
  val pipesPerGeneration = 6
  val pipesKeptPerGeneration = params.GA.pipesKeptPerGeneration
  val layout = List(2,3,2)

  type ReservoirOutput = Vector[Double]
  type FilterOutput    = List[Double]
  type O               = Agent


  /**
    Sets up a simulation running an agent in 5 different initial poses
    aka challenges.
    */
  def createSimRunner(implicit ec: EC): () => Pipe[IO, FilterOutput, O] = {

    println("creating simrunner")
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

      Pull.output(Chunk.seq(initNetworks.map(ANNPipes.ffPipeO[IO](ticksPerEval, _)))) >> go(initNetworks, s)
    }


    def go(previous: List[FeedForward], evals: Stream[IO, Double]): Pull[IO, Pipe[IO, ReservoirOutput, Option[FilterOutput]], Unit] = {
      evals.pull.unconsN((pipesPerGeneration - 1).toLong, false) flatMap {
        case Some((segment, tl)) => {
          val scoredPipes = ScoredSeq(segment.toList.zip(previous).toVector)
          val nextPop = generate(scoredPipes)
          val nextPipes = nextPop.map(ANNPipes.ffPipeO[IO](ticksPerEval, _))
          Pull.output(Chunk.seq(nextPipes)) >> go(nextPop,tl)
        }
      }
    }

    // Generates a new set of neural networks, no guarantees that they'll be any good...
    def generate(seed: ScoredSeq[FeedForward]): List[FeedForward] = {
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
          val closest = seg.toList
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
