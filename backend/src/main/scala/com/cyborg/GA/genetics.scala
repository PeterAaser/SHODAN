package com.cyborg

object genetics {

  import Filters._
  import scala.util.Random

  val severity = 0.2

  def mutate(ff: FeedForward): FeedForward = {
    val t1 = ff.bias.toArray
    val t2 = ff.weights.toArray

    for (i <- 0 until ((t1.length)*severity).toInt){
      if(Random.nextDouble > severity)
        t1(i) = t1(i) + Random.nextDouble()*0.2
    }

    for (i <- 0 until ((t2.length)*severity).toInt){
      if(Random.nextDouble > severity)
        t2(i) = t2(i) + (Random.nextDouble() - 0.5)
    }

    ff.copy(bias = t1.toList, weights = t2.toList)
  }


  def fugg(a: FeedForward, b: FeedForward): (FeedForward, FeedForward) = {
    val aTempBias = a.bias.toArray
    val aTempWeight = a.weights.toArray

    val bTempBias = b.bias.toArray
    val bTempWeight = b.weights.toArray

    for (i <- 0 until aTempBias.length){
      if(Random.nextDouble > severity){
        val tmp = aTempBias(i)
        aTempBias(i) = bTempBias(i)
        bTempBias(i) = tmp
      }
    }

    for (i <- 0 until aTempWeight.length){
      if(Random.nextDouble > severity){
        val tmp = aTempWeight(i)
        aTempWeight(i) = bTempWeight(i)
        bTempWeight(i) = tmp
      }
    }

    (a.copy(bias = aTempBias.toList, weights = aTempWeight.toList),
    b.copy(bias = bTempBias.toList, weights = bTempWeight.toList))

  }

}
