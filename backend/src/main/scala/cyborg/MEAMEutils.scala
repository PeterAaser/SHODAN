package cyborg


import cats.effect.IO
import scala.concurrent.duration._
import scala.math._

import utilz._


/**
  An unholy mess of generic and specific logic.
  Some of the methods rely on a configuration.
  */
object MEAMEutilz {

  type SafeHzTransform = Distance => Frequency
  type Distance = Double
  type Frequency = Double

  import params.game._
  import params.experiment._

  val minDistance: Double = params.game.deadZone
  val maxDistance: Double = params.game.sightRange



  val lnOf2 = scala.math.log(2)
  def log2(x: Double): Double = scala.math.log(x) / lnOf2

  val linear: Double => Double = {
    val a = (maxFreq - minFreq)/(sightRange - minDistance)
    val b = minFreq - a*minDistance

    (d => a*d + b)
  }


  def clamp(f: Double => Double, max: Double, min: Double): Double => Double = { d =>
    val r = f(d)
    if(r < min) min else ( if(r > max) max else r)
  }


  /**
    a linear function ax + b such that
    lin(minDistance) = 0
    lin(maxDistance) = 1
    */
  val lin: Double => Double = {
    val a = 1.0/(minDistance - sightRange)
    val b = -sightRange*a
    x => a*x + b
  }


  /**
    valid domain: {0,1} -> {0,1}
    works for values outside, but these will be clamped in toFreq

    I'd love to express these constraints better, maybe in idris or dotty?
    */
  val expDecay: Double => Double = { x =>
    val d = 1-x
    exp(-d)+((1-d)/E) -1/E
  }


  def toFreq(d: Distance): Frequency = {
    if(d > maxDistance)
      0.0
    else if(d < minDistance)
      maxFreq
    else
      (expDecay(expDecay(lin(d)))*(maxFreq - minFreq)) + minFreq
  }


  def toTickPeriod(d: Double): Int = {
    val period = (1.0/d)
    (period*params.experiment.DSPticksPerSecond).toInt
  }
}
