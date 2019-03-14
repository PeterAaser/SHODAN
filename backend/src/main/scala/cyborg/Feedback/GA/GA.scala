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

  def generate(seed: Seq[ScoredNet]): Seq[FeedForward] = {
    val scoredSeq = ScoredSequeunce(seed)
    // val rouletteScaled = scoredSeq.errorScored.normalize.sortByScore.rouletteScale
    val scored         = scoredSeq.errorScored
    val children       = generateChildrenTournament(scored)
    val mutants        = scored.randomSample(mutantsPerGen).strip.map(mutate)

    children ++ mutants
  }


  private def generateChildrenRoulette(scoredSeq: ScoredSequeunce) : Seq[FeedForward] = {
    val children = Array.ofDim[FeedForward](childrenPerGen)
    for(ii <- 0 until childrenPerGen/2){
      val (mom, dad)        = scoredSeq.sample2
      val (brother, sister) = fugg(mom, dad)
      children(ii*2)        = brother
      children((ii*2)+1)    = sister
    }

    children.toList
  }

  private def generateChildrenTournament(scoredSeq: ScoredSequeunce) : Seq[FeedForward] = {
    val children = Array.ofDim[FeedForward](childrenPerGen)
    for(ii <- 0 until childrenPerGen/2){
      val (mom, dad)        = scoredSeq.tournamentSample2
      val (brother, sister) = fugg(mom, dad)
      say("\n")
      say(mom)
      say(dad)
      children(ii*2)        = brother
      children((ii*2)+1)    = sister
    }

    children.toList
  }
}

object GAsyntax {

  case class ScoredSequeunce(
    repr: Seq[ScoredNet],
    isNormalized: Boolean,
    isSorted: Boolean,
    isScored: Boolean,
    isScaled: Boolean
  ){
    def errorScored = copy(repr.errorScored, isScored = true)

    def normalize = {
      assert(isScored, "Must be scored")
      copy(repr = repr.normalize, isNormalized = true)
    }

    def sortByScore = {
      assert(isScored, "Must be scored")
      copy(repr = repr.sortByScore, isSorted = true)
    }

    def rouletteScale = {
      assert(isScored, "Must be scored")
      assert(isSorted, "Must be sorted")
      assert(isNormalized, "Must be normalized")
      copy(repr = repr.rouletteScale, isScaled = true)
    }

    def sample2 = {
      assert(isScored, "Must be scored")
      assert(isSorted, "Must be sorted")
      assert(isNormalized, "Must be normalized")

      repr.sample2
    }

    def tournamentSample2 = {
      val contestants = randomSample(4)
      (if(contestants(0).error < contestants(1).error) contestants(0).net else contestants(1).net,
       if(contestants(2).error < contestants(3).error) contestants(2).net else contestants(3).net)
    }

    def randomSample(n: Int) =
      repr.randomSample(n)
  }
  object ScoredSequeunce {
    def apply(repr: Seq[ScoredNet]): ScoredSequeunce = {
      ScoredSequeunce(repr, false, false, false, false)
    }
  }

  type ScoredNet = (Double, Double, FeedForward)

  implicit class ScoredNetBonusOps(val repr: ScoredNet) extends AnyVal {
    def error = repr._1
    def score = repr._2
    def net   = repr._3
    def strip = repr._3
  }

  implicit class ScoredSeqBonusOps(val repr: Seq[ScoredNet]) extends AnyVal {

    def errorScored = repr.map(x => (x.error, x.error * -1.0, x.net))
    def errorSum : Double = repr.foldLeft(.0)( (sum, ss) => sum + ss.error)
    def scoreSum : Double = repr.foldLeft(.0)( (sum, ss) => sum + ss.score)
    def sortByScore = repr.sortWith(_.score < _.score)
    def sortByError = repr.sortWith(_.error > _.error)

    /**
      * Sums all scores for a scored sequence then normalizes the score of each
      * network based on the sum.
      */
    def normalize = repr.map(x => (x.error, x.score/scoreSum, x.net))

    /**
      * strips error and score
      */
    def strip = repr.map(_._3)


    /**
      * Scales the scores such that when a number between 0 and 1 is randomly chosen
      * the chance for each number to be picked is equal to their normalized score
      */
    def rouletteScale = {
      // assert((scoreSum <= 1.01) && (scoreSum > 0.99), s"must be normalized first you dunce. was ${scoreSum}")
      val scores = repr.foldLeft((List[Double](), .0)){ case ((acc, prev), (error, score, net)) =>
        ((prev + score) :: acc, prev + score)
      }._1.reverse

      (repr zip scores).map{ case((error, old, network), newScore) => (error, newScore, network)}
    }

    def randomSample(samples: Int) = {
      val indexes = Random.shuffle(0 to repr.size - 1).take(samples)
      indexes.map(x => repr(x))
    }


    /**
      * samples an item from a roulette scaled list analogous to
      * spinning the roulette wheel.
      * 
      * For List(0.1, 0.35, 1.0) the probability of choosing element 
      * 0 is 10%, element 1: 25% and element 2: 65%
      * 
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
    def sample(n: Int): Seq[ScoredNet] = {

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
      indexes.map(idx => repr(idx - 1)).toList
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
