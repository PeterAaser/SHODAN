package cyborg.feedback

import fs2._
import fs2.concurrent._

import cats._
import cats.data._
import cats.implicits._

import cats.effect._
import cats.effect.implicits._
import cats.effect.concurrent._

import cyborg._
import utilz._
import bonus._

import Settings._
import FFANN._
import wallAvoid.Agent


trait Optimizer[Dataset, Phenotype]{
  def enqueueDataset(dataset: Dataset): Optimizer[Dataset, Phenotype]

  /**
    * Runs one iteration and returns the best result along with the new optimizer.
    * This API makes more sense for a GA population than a backprop, but maintaining
    * state is more general.
    */
  def iterate: (Optimizer[Dataset, Phenotype], ScoredPhenotype[Phenotype])
  def topResult: Phenotype
}


/**
  * An online optimizer is responsible for concurrently testing out new readout layers
  * running 
  */
class OnlineOptimizer[F[_]: Concurrent, Dataset, Phenotype](
  val optimizer      : SignallingRef[F,Optimizer[Dataset, Phenotype]],
  val bestResult     : SignallingRef[F,ScoredPhenotype[Phenotype]],
  val pauseSignal    : SignallingRef[F,Boolean],
  val datasetUpdates : SignallingRef[F,List[Dataset]]
) {

  def updateDataset(rec: Dataset): F[Unit] = for {
    _ <- datasetUpdates.update(c => rec :: c)
    _ <- pauseSignal.set(false)
  } yield ()

  def start: Stream[F, Unit] = {
    pauseSignal.discrete.tail.changes.flatMap{ _ =>

      def updateBest(contender: ScoredPhenotype[Phenotype]): F[Unit] = bestResult.update{ prev =>
        say("Updating best result!!")
        if(prev.score > contender.score) {
          say("Current champion holds the title")
          say(prev)
          say(contender)
          prev
        }
        else {
          say("New champion!")
          say(contender)
          contender
        }
      }

      // Doesn't need to be phrased in terms of Pull at all actually
      def go: Pull[F,Unit,Unit] = {
        val task = for {
          next <- datasetUpdates.modify(set => (set, Nil))
          _    <- optimizer.update(o => next.foldLeft(o){ case(acc, set) => acc.enqueueDataset(set)})
          best <- optimizer.modify(o => o.iterate)
          _    <- updateBest(best)
        } yield ()

        Pull.eval(task)
      }

      go.stream.repeat
    }
  }
}

object OnlineOptimizer {

  type Dataset = Array[(Agent, Chunk[Double])]

  def GA[F[_]: Concurrent](conf: FullSettings): F[OnlineOptimizer[F,Dataset,FeedForward]] = {

    import cyborg.feedback.ga._

    val initPop       = List.fill(conf.ga.generationSize)(cyborg.FFANN.randomNet(conf.readout))
    val initPopScored = initPop.map(x => ScoredPhenotype(-1000000000.0, 1000000000.0, x))
    val optimizer: Optimizer[Dataset, FeedForward] = GAoptimizer(conf, Scored(initPopScored))

    for {
      pause     <- SignallingRef[F, Boolean](true)
      optimizer <- SignallingRef(optimizer)
      best      <- SignallingRef(initPopScored.head)
      updates   <- SignallingRef(List[Dataset]())
    } yield new OnlineOptimizer(optimizer, best, pause, updates)
  }
}
