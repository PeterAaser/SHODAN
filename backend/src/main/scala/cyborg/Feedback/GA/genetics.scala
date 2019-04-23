package cyborg.feedback.ga

import cyborg.utilz._

object Genetics {

  import cyborg.FFANN._
  import scala.util.Random

  val severity = 0.5
  val mutationSeverity = 0.2

  def mutate(ff: FeedForward): FeedForward = {
    val t1 = ff.weights.toArray

    for (i <- 0 until ((t1.length)*severity).toInt){
      if(Random.nextDouble < severity)
        t1(i) = t1(i) + (Random.nextDouble()*0.1 - 0.05)
    }

    ff.copy(weights = t1.toList)
  }


  def fugg(a: FeedForward, b: FeedForward): (FeedForward, FeedForward) = {

    if(a.weights.toList == b.weights.toList){
      (mutate(a), mutate(a))
    }
    else {

      val aTempWeights = a.weights.toArray
      val bTempWeights = b.weights.toArray

      for (i <- 0 until aTempWeights.length){
        if(Random.nextDouble > severity){
          val tmp = aTempWeights(i)
          aTempWeights(i) = bTempWeights(i)
          bTempWeights(i) = tmp
        }
      }


      (a.copy(weights = aTempWeights.toList),
      b.copy(weights = bTempWeights.toList))

    }
  }
}
