package cyborg.feedback

import scala.Function2

import cats.effect._
import cats._

import cyborg.utilz._
import cyborg.FFANN._
import cyborg.Settings._

import scala.util.Random

/**
  * Kinda ugly to put GA in the ga package object. Sorry :(
  */
package object ga {

  type Datatype = Array[(cyborg.wallAvoid.Agent, fs2.Chunk[Double])]

  import Genetics._
  import ScoredSeqOps._

  /**
    * Would be type parametrized, however the genetics class is not parametrized, so
    * we deal with feedforward exclusively atm.
    */
  case class GA(conf: FullSettings) {
    import conf.ga._
  
    def generate(seed: Scored[FeedForward]): Seq[FeedForward] = {
      val children = seed
        .errorScored
        .tournamentSample(2, conf.ga.childrenPerGen)
        .grouped(2)
        .flatMap{x =>
          val (a, b) = Genetics.fugg(x(0), x(1))
          List(a,b)
        }
        .toList
  
      val mutants  = seed.randomSample(conf.ga.mutantsPerGen).phenotypes
      children ++ mutants
    }
  
    private def genRoulette(seed: Scored[FeedForward]) : Seq[FeedForward] = ???
  
    private def genTournament(seed: Scored[FeedForward]) : Seq[FeedForward] = {
      val parents = seed.tournamentSample(2, conf.ga.childrenPerGen)
      val children = parents.grouped(2).flatMap{parents =>
        val (a, b) = fugg(parents(0), parents(1))
        List(a, b)
      }.toList
  
      children
    }
  }
}
