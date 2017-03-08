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

  trait stimTransform extends (List[Int] => (List[Int], List[Double]))

  type Hz = Double
  type Sec = Double
  type TickP = Int
  type Dist = Double

  val sightRange: Dist = 3000

  val maximumFrequency: Hz = 10.0
  val minimumFrequency: Hz = 1.0/3.0
  val ticksPerSecond = 40000
  val minimumDistance: Dist = 100
  val maximumDistance: Dist = sightRange

  val lnOf2 = scala.math.log(2) // natural log of 2
  def log2(x: Double): Double = scala.math.log(x) / lnOf2

  val linear: Dist => Hz = {
    val a = (maximumFrequency - minimumFrequency)/(sightRange - minimumDistance)
    val b = minimumFrequency - a*minimumDistance
    d: Dist => a*d + b
  }


  // The function will be on the form of exp(x-h) + c, so we must find h and c
  def logScaleBuilder(base: Double): Dist => Hz = {

    val (_exp: (Double => Double), _log: (Double => Double)) = base match {
      case scala.math.E => (exp _, log _)
      case b => {
        val natLogb = log(b)
        val logb: Double => Double = λ => log(λ)/natLogb
        val expb: Double => Double = λ => pow(b, λ)
        (expb, logb)
      }
    }

    val λ = minimumFrequency
    val µ = maximumFrequency

    val m = maximumDistance
    val q = minimumDistance

    val h = _log((λ-µ)/(_exp(m) - _exp(q)))
    val k = λ - _exp(m-h)

    d => _exp(d-h) + k
  }

  val toTick: Hz => TickP = {
    h: Hz => scala.math.floor(ticksPerSecond/h).toInt
  }


  def toStimFrequency(electrodes: List[Int], transform: Double => Double): List[Double] => String =
    distances => utilz.simpleJsonAssembler(electrodes, distances.map(transform))
}
