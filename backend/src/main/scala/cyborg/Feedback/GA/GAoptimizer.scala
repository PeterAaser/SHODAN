package cyborg.feedback.ga

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

import cyborg.feedback._

/**
  * Maintains a scored population of feed forward nets which can be iterated, providing (hopefully) 
  * better networks.
  */
case class GAoptimizer(
  conf             : FullSettings,
  population       : Scored[FeedForward],
  enqCounter       : Int = 0,
  recordings       : Datatype,
  agentInputBuffer : Array[(Double, Double)]
) extends Optimizer[Array[(Agent, Chunk[Double])], FeedForward] {
 
  val ga = GA(conf)
  import ga._
  import ScoredSeqOps._

  type ScoredNet = ScoredPhenotype[FeedForward]
  type FilterOutput  = Chunk[Double]
  type ReadoutOutput = Chunk[Double] // aka (Double, Double)

  import conf.optimizer._
  val recordingSize = dataSetWindow * dataSetSize

  var enqCounterRef = enqCounter


  /**
    * Removes most of the points from the dataset given by some 
    * decimationfactor specified in the configuration.
    * 
    * Ugly and side-effecting.
    * Not race condition proof, the set will likely be altered during iterations.
    * 
    * The caller is responsible for this call to be sequential (see class OnlineOptimizer)
    */
  private def decimateDataSet(rec: Array[(Agent, FilterOutput)]): Unit = {
    val recordingIdx = enqCounter % dataSetWindow
    val copyIndexBase = dataSetSize*recordingIdx
    for(ii <- 0 until dataSetSize){
      val readIndex = ii*decimationFactor
      recordings(copyIndexBase + ii) = rec(readIndex)
    }
    enqCounterRef += 1
  }


  /**
    * Evaluates a single network with a dataset.
    */
  private def evaluateNet(readoutLayer: FeedForward, errorFunction: (Agent, Agent) => Double): ScoredNet = {

    val maxIndex = if(enqCounter > dataSetWindow) dataSetSize * dataSetWindow
      else dataSetSize * enqCounter


    // Calculate the output from the net at recorded inputs
    for(ii <- 0 until maxIndex){
      val input = readoutLayer.feed(recordings(ii)._2)
      agentInputBuffer(ii) = (input(0), input(1))
    }

    var error = 0.0
    for(ii <- 0 until maxIndex){
      val ideal    = recordings(ii)._1.autopilot
      val observed = recordings(ii)._1.update(agentInputBuffer(ii)._1, agentInputBuffer(ii)._2)
      error += errorFunction(ideal, observed)
    }

    ScoredPhenotype[FeedForward](0.0, error, readoutLayer)
  }


  def enqueueDataset(dataset: Array[(Agent, Chunk[Double])]): GAoptimizer = {
    decimateDataSet(dataset)

    val nextPop = if(enqCounter > dataSetWindow){
      population.copy(repr = population.repr.map(ph => ph.copy(error = ph.error * 1.1)))
    }
    else 
      population.copy(repr = population.repr.map(ph => ph.copy(error = ph.error * 2.0)))

    this.copy(enqCounter = enqCounterRef, population = nextPop)
  }


  def linearDiff(ideal: Agent, observed: Agent) = Math.abs(ideal.heading - observed.heading)
  def squareDiff(ideal: Agent, observed: Agent) = Math.pow(Math.abs(ideal.heading - observed.heading), 2.0)
  def rootDiff(ideal: Agent, observed: Agent)   = Math.sqrt(Math.abs(ideal.heading - observed.heading))


  def iterate: (GAoptimizer, ScoredPhenotype[FeedForward]) = {

    val nextGenScored = ga.generate(population).map(evaluateNet(_, linearDiff))

    val nextPopScored = population
      .copy(repr = population.repr ++ nextGenScored)
      .errorScored
      .sortByScore
      .dropWorst(conf.ga.dropPerGen)

    // val popString = nextPopScored.repr.map(ph => "%1.4f".format{ph.score})
    // say("top 20")
    // say(popString.takeRight(20).mkString("\n"))

    // val popNames = nextPopScored.repr.map(ph => ph.phenotype.name)
    // say("top 20")
    // say(popNames.takeRight(20).mkString("\n"))

    // checkDuplicates
    // checkNextGenDiversity(nextPopScored)

    (this.copy(population = nextPopScored), nextPopScored.topPerformer)
  }


  def topResult: FeedForward = population.topPerformer.phenotype

  def checkNextGenDiversity(next: Scored[FeedForward]): Unit = {
    val prevNets = population.repr.map(_.phenotype.weights.toList).toSet
    val nextNets = next.repr.map(_.phenotype.weights.toList).toSet

    val unique = prevNets.intersect(nextNets).size

    say(s"comparing gens: unique: $unique")
  }

  def checkDuplicates: Unit = {
    val unique = population.repr.map(_.phenotype.weights.toList).toSet.size
    val total  = population.repr.size
    say(s"unique phenos: $unique")
    say(s"total phenos: $total")
  }
}

object GAoptimizer {

  type FilterOutput  = Chunk[Double]
  type ReadoutOutput = Chunk[Double] // aka (Double, Double)

  def apply(conf: FullSettings, population: Scored[FeedForward]): GAoptimizer = {

    import conf.optimizer._
    val recordingSize = conf.optimizer.dataSetWindow * conf.optimizer.dataSetSize

    val recordings: Array[(Agent, FilterOutput)] = Array.ofDim[(Agent, FilterOutput)](recordingSize)
    val agentInputBuffer: Array[(Double, Double)] = Array.ofDim[(Double, Double)](recordingSize)

    GAoptimizer(conf, population, 0, recordings, agentInputBuffer)
  }
}
