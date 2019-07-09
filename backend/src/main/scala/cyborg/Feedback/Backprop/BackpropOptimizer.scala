package cyborg.feedback.backprop

import fs2._
import fs2.concurrent._

import cats._
import cats.data._
import cats.implicits._

import cats.effect._
import cats.effect.implicits._
import cats.effect.concurrent._

import cyborg._
import cyborg.feedback._

import utilz._
import bonus._

import Settings._
import FFANN._
import wallAvoid.Agent


/**
  * Iterates on a feed forward neural network using backprop, providing
  * (hopefully) better networks
  */
case class BackPropOptimizer(
  conf             : FullSettings,
  enqCounter       : Int = 0,
  recordings       : Datatype,
  agentInputBuffer : Array[(Double, Double)]
) extends Optimizer[Array[(Agent, Chunk[Double])], FeedForward] {

  import conf.optimizer._
  type Dataset = Array[(Agent, Chunk[Double])]
  type Phenotype = FeedForward


  /**
    * Copies the enqueued data into the optimizers internal datastore.
    * In spite of the signature, the return type could just as well have
    * beet Unit, so be careful.
    * 
    * Ignores dataset decimation which is more useful for the GA
    */
  def enqueueDataset(dataset: Dataset): Optimizer[Dataset, Phenotype] = {
    val recordingIdx = enqCounter % dataSetWindow
    val copyIndexBase = ticksPerEval*recordingIdx

    dataset.copyToArray(recordings, copyIndexBase, dataset.size)
    this.copy(enqCounter = enqCounter + 1)
  }


  /**
    * Runs one iteration and returns the best result along with the new optimizer.
    * This API makes more sense for a GA population than a backprop, but maintaining
    * state is more general.
    */
  def iterate: (Optimizer[Dataset, Phenotype], ScoredPhenotype[Phenotype]) = ???
  def topResult: Phenotype = ???
}
