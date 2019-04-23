package cyborg.feedback.ga

import cyborg.feedback._
import cyborg.utilz._
import cyborg.FFANN._

import Genetics._

import scala.util.Random

final case class Scored[A](
  repr         : Seq[ScoredPhenotype[A]],
  isNormalized : Boolean = false,
  isSorted     : Boolean = false,
  isScored     : Boolean = false,
  isScaled     : Boolean = false
){
  import ScoredSeqOps._

  // def getDuplicates

  /**
    * An error-scored collection is rescored based 
    */
  def errorScored = copy(repr = repr.errorScored, isScored = true)

  def normalize: Scored[A] = {
    assert(isScored,     "Must be scored")
    copy(repr = repr.normalize, isNormalized = true)
  }

  def sortByScore: Scored[A] = {
    assert(isScored,     "Must be scored")
    copy(repr = repr.sortByScore, isSorted = true)
  }

  def rouletteScale: Scored[A] = {
    assert(isScored,     "Must be scored")
    assert(isSorted,     "Must be sorted")
    assert(isNormalized, "Must be normalized")
    copy(repr = repr.rouletteScale, isScaled = true)
  }

  def sample2: (A, A) = {
    assert(isScored,     "Must be scored")
    assert(isSorted,     "Must be sorted")
    assert(isNormalized, "Must be normalized")

    repr.sampleUnique2
  }

  def tournamentSample(numberOfContestants: Int, samples: Int) = {
    assert(isScored,     "Must be scored")
    repr.tournamentSample(numberOfContestants, samples)
  }

  def dropWorst(dropAmount: Int) = {
    assert(isSorted,     "Must be sorted")
    copy(repr = repr.drop(dropAmount))
  }

  def randomSample(n: Int) = repr.randomSample(n)

  def topPerformer = {
    assert(isSorted,     "Must be sorted")
    repr.last
  }
}


object ScoredSeqOps {
  implicit class ScoredSeqBonusOps[A](val repr: Seq[ScoredPhenotype[A]]) {

    def errorScored = repr.map(x => x.copy(score = x.error * -1.0))
    def sortByScore = repr.sortWith(_.score < _.score)
    def sortByError = repr.sortWith(_.error > _.error)

    def errorSum : Double = repr.foldLeft(.0)( (sum, ss) => sum + ss.error)
    def scoreSum : Double = repr.foldLeft(.0)( (sum, ss) => sum + ss.score)


    /**
      * Sums all scores for a scored sequence then normalizes the score of each
      * network based on the sum.
      */
    def normalize: Seq[ScoredPhenotype[A]] = repr.map(x => x.copy(score = x.score/scoreSum))


    /**
      * strips error and score
      */
    def phenotypes: Seq[A] = repr.map(_.phenotype)


    /**
      * Scales the scores such that when a number between 0 and 1 is randomly chosen
      * the chance for each number to be picked is equal to their normalized score
      * Input must be sorted and normalized.
      */
    def rouletteScale = {
      val scores = repr.foldLeft((List[Double](), .0)){ case ((acc, prev), phenotype) =>
        ((prev + phenotype.score) :: acc, prev + phenotype.score)
      }._1.reverse

      (repr zip scores).map{ case(phenotype, newScore) => phenotype.copy(score = newScore)}
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
    private def biasedSample(seed: Double): Int = {
      if(seed < repr.head.score){
        0
      }
      else if(seed > repr.last.score){
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
        seekIndex
      }
    }


    def sample1 = {
      val seed = Random.nextDouble()
      repr(biasedSample(seed))
    }


    /**
      * Select n unique parents
      */
    def sampleUnique(n: Int): Array[ScoredPhenotype[A]] = {

      val indexes = Array.fill[Int](n)(-1)

      for(ii <- 0 until n){
        def seed = Random.nextDouble()

        var idx = biasedSample(seed)
        while(indexes.contains(idx)){
          idx = biasedSample(seed)
        }

        indexes(ii) = idx
      }
      indexes.map(idx => repr(idx - 1))
    }


    /**
      * Select two unique parents
      */
    def sampleUnique2: (A, A) = {
      def seed = Random.nextDouble()
      val a = biasedSample(seed)
      var b = biasedSample(seed)
      while(a == b)
        b = biasedSample(seed)

      (repr(a).phenotype, repr(b).phenotype)
    }


    /**
      * Selects N random contestants and selects the best one
      */
    def tournamentSample(numberOfContestants: Int, samples: Int): Seq[A] = {
      List.fill(numberOfContestants*samples)(Random.nextInt(repr.size - 1))
        .map(idx => repr(idx))
        .grouped(numberOfContestants)
        .toList
        .map(_.maxBy(_.score))
        .map(_.phenotype)
    }
  }
}
