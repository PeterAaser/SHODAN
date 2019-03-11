package cyborg

import scala.Function2

import cats.effect._
import cats._

import utilz._
import FFANN._

import Genetics._
import scala.util.Random
import Settings._

class GA(conf: FullSettings) {
  import GAsyntax._
  import conf.ga._

  def generate(seed: Array[(Double, FeedForward)]): Array[FeedForward] = {
    val rouletteScaled = seed.normalize.rouletteScale
    val children       = generateChildren(rouletteScaled)
    val mutants        = rouletteScaled.sample(mutantsPerGen).strip.map(mutate)

    children ++ mutants
  }


  def generateChildren(seed: Array[(Double, FeedForward)]) : Array[FeedForward] = {
    val children = Array.ofDim[FeedForward](childrenPerGen)
    for(ii <- 0 until childrenPerGen/2){
      val (mom, dad)        = seed.sample2
      val (brother, sister) = fugg(mom, dad)
      children(ii*2)        = brother
      children((ii*2)+1)    = sister
    }

    children
  }
}


object GAsyntax {

  implicit class ScoredNetBonusOps(val repr: (Double, FeedForward)) extends AnyVal {
    def score = repr._1
    def net   = repr._2
  }

  implicit class ScoredSeqBonusOps(val repr: Array[(Double, FeedForward)]) extends AnyVal {

    def errorScored = repr.map(x => (x._1 * -1.0, x._2))
    def scoreSum : Double = repr.foldLeft(.0)( (sum, ss) => sum + ss._1)
    def sortByScore = repr.sortWith(_._1 < _._1)

    /**
      Sums all scores for a scored sequence then normalizes the score of each
      network based on the sum.
      */
    def normalize = repr.map(x => (x._1/scoreSum, x._2))

    def strip = repr.map(_._2)

    /**
      Scales the scores such that when a number between 0 and 1 is randomly chosen
      the chance for each number to be picked is equal to their normalized score
      */
    def rouletteScale = {

      assert((scoreSum <= 1.01) && (scoreSum > 0.99), s"must be normalized first you dunce. was ${scoreSum}")
      val scores = repr.foldLeft((List[Double](), .0)){ case ((acc, prev), (score, net)) =>
        ((prev + score) :: acc, prev + score)
      }._1.reverse.toArray

      (repr zip scores).map{ case((old, network), newScore) => (newScore, network)}
    }

    def randomSample(samples: Int) = {
      val indexes = Random.shuffle(0 to repr.size - 1).take(samples)
      indexes.map(x => repr(x))
    }


    /**
      * Checks for boundary conditions, then does a binary search
      * 
      * Please don't call on something that isn't roulettescaled!
      * Returns an index such that we can guarantee that we don't get
      * duplicates
      */
    private def pToIndex(seed: Double): Int = {
      if(seed < repr.head.score){
        // say(s"returning 0 where value ${repr(0).score} was found")
        // say(s"$seed < ${repr(0).score}")
        0
      }
      else if(seed > repr.last.score){
        // say(s"returning ${repr.size - 1} where value ${repr.last.score} was found")
        // say(s"$seed > ${repr.last.score}")
        repr.size - 1
      }
      else {
        var seekIndexMax = repr.size - 1
        var seekIndexMin = 1

        var seekIndex = repr.size/2
        var seekScoreMin = repr(seekIndex - 1).score
        var seekScoreMax = repr(seekIndex).score

        while(seed <= seekScoreMin || seekScoreMax < seed){

          if(seed <= seekScoreMin){
            seekIndexMax = seekIndex - 1
            seekIndex = seekIndexMin + (seekIndexMax - seekIndexMin)/2

            seekScoreMin = repr(seekIndex - 1).score
            seekScoreMax = repr(seekIndex).score
          }

          else{
            seekIndexMin = seekIndex + 1
            seekIndex = seekIndexMin + (seekIndexMax - seekIndexMin)/2

            seekScoreMin = repr(seekIndex - 1).score
            seekScoreMax = repr(seekIndex).score
          }
        }
        // say(s"Returnig $seekIndex, where value ${repr(seekIndex).score} was found")
        // say(s"$seekScoreMin < ${seed} < ${seekScoreMax}")
        seekIndex
      }
    }

    def sample1 = {
      val seed = Random.nextDouble()
      repr(pToIndex(seed))
    }


    /**
      * Deadlocks if called with bad args :^)
      */
    def sample(n: Int) = {

      assert(repr.map(_.score).toList == repr.map(_.score).toList.sorted, s"must be sorted first you dunce. was ${scoreSum}")

      // say(s"sample $n")
      val indexes = Array.ofDim[Int](n)
      for(ii <- 0 until n){

        // def so we can call it multiple times and get different results. The PHP way
        def seed = Random.nextDouble()

        // naughty trick, let's use use contains without reinitializing array.
        var idx = pToIndex(seed) + 1

        // Searches for an index that has yet to be found
        while(indexes.contains(idx)){
          idx = pToIndex(seed) + 1
        }

        indexes(ii) = idx
      }
      indexes.map(idx => repr(idx - 1))
    }


    /**
      * Get a pair of parents
      */
    def sample2 = {
      def seed = Random.nextDouble()
      val a = pToIndex(seed)
      var b = pToIndex(seed)
      while(a == b)
        b = pToIndex(seed)

      (repr(a).net, repr(b).net)
    }
  }
}
