package cyborg

object Genetics {

  import FFANN._
  import scala.util.Random

  val severity = 0.2

  def mutate(ff: FeedForward): FeedForward = {
    val t1 = ff.weights.toArray

    for (i <- 0 until ((t1.length)*severity).toInt){
      if(Random.nextDouble > severity)
        t1(i) = t1(i) + Random.nextDouble()*0.2
    }

    ff.copy(weights = t1.toList)
  }


  def fugg(a: FeedForward, b: FeedForward): (FeedForward, FeedForward) = {
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
