package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.async.mutable.Queue

/**
  Responsible for trying different neural networks as well as handling the
  unpleasentries dealing with queues etc.

  Currently not really a GA, just a mockup for the sake of API and some results
  */
object ffGA {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  import Filters._
  import Filters.FeedForward._
  import seqUtils._
  import genetics._
  import wallAvoid._

  // >Hardcoded parameters
  // >Typesafe config
  // >Params from DB
  // nice MEAME :^)

  // hardcoded
  val pipesPerGeneration = 6
  val newPipesPerGeneration = 2
  val newMutantsPerGeneration = 1
  val pipesKeptPerGeneration = pipesPerGeneration - (newPipesPerGeneration + newMutantsPerGeneration)

  // Generates a new set of neural networks, no guarantees that they'll be any good...
  def generate(seed: ScoredSeq[FeedForward[Double]]): List[FeedForward[Double]] = {
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

    def go(previous: List[FeedForward[Double]]):
               Handle[F,Double] => Pull[F,Pipe[F,ffANNinput,ffANNoutput],Unit] = h =>
      {
        h.awaitN(pipesPerGeneration,false) flatMap {
          case(chunks,h) => {
            val folded = chunks.foldLeft(List[Double]())(_++_.toList).zip(previous)
            val asScored = ScoredSeq(folded.toVector)
            val nextPop = generate(asScored)
            val nextPipes = nextPop.map(ANNPipes.ffPipe[F](_))
            Pull.output(Chunk.seq(nextPipes)) >> go(nextPop)(h)
          }
        }
      }

    val initNetworks =
      (0 to pipesPerGeneration).map(_ => (randomNetWithLayout(layout))).toList

    _.pull(go(initNetworks))
  }


  /**
    Sets it all up yo
    */
  // TODO figure out the initial part
  def experimentPipe[F[_]:Async](inStream: Stream[F,ffANNinput], layout: List[Int]):
      Stream[F,Stream[F,Agent]] = {

    val inputQueueTask = fs2.async.unboundedQueue[F,ffANNinput]
    val evaluationQueueTask = fs2.async.unboundedQueue[F,Double]

    val initPipes: Stream[F,Pipe[F,ffANNinput,ffANNoutput]] = ???

    def loop(
      pipes: Stream[F,Pipe[F,ffANNinput,ffANNoutput]],
      inputQueue: fs2.async.mutable.Queue[F,ffANNinput],
      evalQueue: fs2.async.mutable.Queue[F,Double]): Stream[F,Agent] =
    {

      //hardcoded
      def evalFunc: Double => Double = x => x
      val ticksPerEval = 1000

      // Create scenario pipe, attach evaluator sink
      val evaluatingAgentPipes: Stream[F,Pipe[F,ffANNinput,Agent]] =
        pipes.through(_.map(_ andThen (agentPipe.evaluatorPipe(ticksPerEval, evalFunc, evalQueue.enqueue))))

      // Consolidate into single pipe
      val evaluatingAgentPipe = pipe.join(evaluatingAgentPipes)

      // We run the input through the consolidated pipe
      val flow = inputQueue.dequeue.through(evaluatingAgentPipe)

      // TODO
      // Not sure if exp batch pipe keeps state here (it don't...)
      val nextPipeStream = evalQueue.dequeue.through(experimentBatchPipe(layout))

      flow ++ loop(nextPipeStream, inputQueue, evalQueue)
    }

    for {
      evalQueue <- Stream.eval(evaluationQueueTask)
      inputQueue <- Stream.eval(inputQueueTask)
    } yield {

      val enqueueStream = inStream.through(inputQueue.enqueue).drain
      val runner = loop(initPipes, inputQueue, evalQueue)

      enqueueStream merge runner
    }
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
      Iterates through a list, picks the element if
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
