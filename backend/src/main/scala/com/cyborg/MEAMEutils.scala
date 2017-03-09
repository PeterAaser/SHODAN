package com.cyborg

import com.typesafe.config._

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.async.mutable.Queue
import fs2.util.syntax._
import fs2.io.file._
import fs2.io.tcp._

import scala.math._

object MEAMEutilz {

  type DistToHz = Double => Double


  val sightRange: Double = 3000

  val maxFreq = 10.0
  val minFreq = 1.0/3.0
  val ticksPerSecond: Int = 40000

  val maxTicks: Int = floor(ticksPerSecond.toDouble/minFreq).toInt
  val minTicks: Int = floor(ticksPerSecond.toDouble/maxFreq).toInt

  val minDistance: Double = 100
  val maxDistance: Double = sightRange


  val lnOf2 = scala.math.log(2) // natural log of 2
  def log2(x: Double): Double = scala.math.log(x) / lnOf2

  val linear: Double => Double = {
    val a = (maxFreq - minFreq)/(sightRange - minDistance)
    val b = minFreq - a*minDistance

    (d => a*d + b)
  }


  // The function will be on the form of exp(x/λ)
  def logScaleBuilder(base: Double): Double => Double = {

    val (_exp: (Double => Double), _log: (Double => Double)) = base match {
      case scala.math.E => (exp _, log _)
      case b => {
        val natLogb = log(b)
        val logb: Double => Double = λ => log(λ)/natLogb
        val expb: Double => Double = λ => pow(b, λ)
        println(s"not base euler, divide with $natLogb")
        (expb, logb)
      }
    }

    val interval = maxDistance - minDistance
    val freqRelation = minFreq/maxFreq

    val λ = interval/_log(freqRelation)

    (d => _exp((d - minDistance)/λ)*maxFreq)
  }


  val toTick: Double => Int = {
    h: Double => scala.math.floor(ticksPerSecond/h).toInt
  }


  def setDomain(f: Double => Double): DistToHz = d =>
    if (d < minDistance) maxFreq else ( if (d > maxDistance) 0 else f(d))


  def toStimFrequency(electrodes: List[Int], transform: DistToHz): List[Double] => String = {
    val t = setDomain(transform)
    distances => utilz.simpleJsonAssembler(electrodes, distances.map(t))
  }
}
