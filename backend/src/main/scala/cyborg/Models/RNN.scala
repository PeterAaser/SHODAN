package cyborg

import cats._
import cats.data._
import cats.implicits._

import cats.effect._
import cats.effect.concurrent._

import fs2._
import fs2.concurrent.Topic
import fs2.concurrent.SignallingRef
import fs2.concurrent.Queue

import utilz._
import bonus._
import cats._
import cats.implicits._

class RNN(
  nodesAmount       : Int,
  k                 : Int,
  mean              : Double,
  variance          : Double,
  dampening         : Double,
  outputNodesAmount : Int,
  stimAmplitude     : Double,
  perturbationRatio : Double,
){

  /**
    * A/B buffering states. A buffer updates B, then B updates A, hence
    * double mutability shenanigans so we can swap them each tick.
    * 
    * Finding it easier to use mutable state here rather than passing around
    * a ton of arguments. All outputs are cloned
   */
  private var nodesCurrent = List.fill(nodesAmount)(0.0).toArray
  private var nodesNext    = List.fill(nodesAmount)(0.0).toArray
  private var currentTick  = 0
  private var fire0        = false
  private var fire1        = false

  private val stimPeriods : Array[Int] = Array(0, 0)
  private val nextStim    : Array[Option[Int]] = Array(None, None)

  private val logArray    = Array.ofDim[String](100)
  private var logCount    = 0

  private val connections: Array[Int] = ((0 until nodesAmount).toList >>
    scala.util.Random.shuffle((0 until nodesAmount).toList).take(k)).toArray

  private val weights: Array[Double] = ((0 until nodesAmount).toList >>
    List.fill(k)((scala.util.Random.nextGaussian*variance) + mean)).toArray

  private val outputNodes = scala.util.Random
    .shuffle((0 until nodesAmount).toList)
    .take(40)

  private val (perturbationNodes0, perturbationNodes1) = scala.util.Random
    .shuffle((0 until nodesAmount).toList).take((nodesAmount*perturbationRatio).toInt).splitAt(100)


  def enableStimGroup(group: Int): Unit = {
    if(stimPeriods(group) === 0)
      say("Setting period to zero is probably an error", Console.RED)

    nextStim(group) = nextStim(group) match {
      case None    => Some(currentTick + stimPeriods(group))
      case Some(n) => Some(n)
    }
  }

  def disableStimGroup(group: Int): Unit =
    nextStim(group) = None

  def updateStimGroup(group: Int, nextPeriod: Int): Unit = {
    nextStim(group) match {
      case Some(nextTick) => {
        val stepsToFire = nextTick - currentTick
        val updatedStepsToFire = stepsToFire - (nextPeriod - stimPeriods(group))
        if(updatedStepsToFire > 0)
          nextStim(group) = Some(updatedStepsToFire + currentTick)
        else
          nextStim(group) = Some(1 + currentTick)
      }

      case None => stimPeriods(group) = nextPeriod
    }

    stimPeriods(group) = nextPeriod
  }


  private def applyWeights: Unit = for(ii <- 0 until nodesAmount*k){
    val recipientIndex = ii/k
    val ins = nodesCurrent(connections(ii))*weights(ii)
    nodesNext(recipientIndex) += ins
  }


  private def applyPerturbation: Unit = {
    if(fire0){
      perturbationNodes0.foreach(idx =>
        nodesNext(idx) += stimAmplitude
      )
    }
    if(fire1){
      perturbationNodes1.foreach(idx =>
        nodesNext(idx) += stimAmplitude
      )
    }
  }


  // They can't all be zingers
  private def updateTick: Unit = {
    nextStim(0) match {
      case Some(fireAt) if(fireAt == currentTick) => {
        fire0 = true
        nextStim(0) = Some(fireAt + stimPeriods(0))
      }
      case _ => {
        fire0 = false
      }
    }

    nextStim(1) match {
      case Some(fireAt) if (fireAt == currentTick) => {
        fire1 = true
        nextStim(1) = Some(fireAt + stimPeriods(1))
      }
      case _ => {
        fire1 = false
      }
    }
  }


  private def activate: Unit = for(ii <- 0 until nodesAmount){
    if((nodesNext(ii) + dampening) >= 0.0)
      nodesNext(ii) = 1.0
    else
      nodesNext(ii) = 0.0
  }



  def stepN(n: Int): Chunk[Chunk[Double]] = {
    val outBuffer = Array.ofDim[Double](n, outputNodesAmount)
    for(ii <- 0 until n){
      applyWeights
      updateTick
      applyPerturbation
      activate
      outBuffer(ii) = outputNodes.map{x =>
        nodesNext(x)
      }.toArray

      val outs = outputNodes.map(x => nodesNext(x))

      val tmp = nodesNext
      nodesNext = nodesCurrent
      nodesCurrent = tmp
      for(ii <- 0 until nodesAmount)
        nodesNext(ii) = 0.0

      currentTick += 1

    }
    outBuffer.map(_.toChunk).toChunk
  }
}
