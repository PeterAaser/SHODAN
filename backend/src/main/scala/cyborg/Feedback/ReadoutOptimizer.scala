package cyborg

import fs2._
import fs2.concurrent._

import cats._
import cats.data._
import cats.implicits._

import cats.effect._
import cats.effect.implicits._
import cats.effect.concurrent._

import utilz._
import bonus._

import Settings._
import FFANN._
import Genetics._
import wallAvoid.Agent

/**
  * The readout optimizer provides a service that runs in parallel, continuously
  * optimizing readout layers as more data becomes available.
  * 
  * Exposes a signal which holds the current best performer for use in a live
  * experiment.
  * 
  * Currently specialized for the maze task.
  * 
  * The internal representation of the readout layer should only be known to the
  * evaluator (so FFANN should only be used here!)
  */
// Contravariant so I can use List for Seq
class ReadoutOptimizer[F[_]: Concurrent](
  conf               : FullSettings,
  simRunnerEvaluator : Pipe[F,Chunk[Double],Double],
  bestResult         : SignallingRef[F,(Double, Pipe[F,Chunk[Double],Chunk[Double]])],
  pauseSignal        : SignallingRef[F,Boolean],
  updateSignal       : SignallingRef[F,Boolean]
) {

  val ga = new GA(conf)
  import ga._
  import GAsyntax._

  type FilterOutput  = Chunk[Double]
  type ReadoutOutput = Chunk[Double] // aka (Double, Double)

  // A record of what the agent saw, and what the reservoir returned (filtered)
  type Run     = (Agent, Chunk[FilterOutput])

  // A dataset contains N runs for some network, each run annotated with a starting agent
  type DataSet = Chunk[(Agent, Chunk[FilterOutput])]

  val recordings = new scala.collection.mutable.Queue[DataSet]()
  def enq(rec : DataSet): F[Unit] = {
    Sync[F].delay{
      recordings.enqueue(rec);
      say(rec)
      if(recordings.size > 6) recordings.dequeue_
    } >>
    Fsay[F]("enqueued!") >>
    Fsay[F](s"${recordings.size}") >>
    pauseSignal.set(false) >>
    updateSignal.set(true) >>
    bestResult.update{ case(error, pipe) =>
      val factor = if(recordings.size < 6) 2.0 else 1.1
      (error*factor, pipe)
    }
  }

  val deq  : F[DataSet] = Sync[F].delay{ recordings.dequeue }
  val deq_ : F[Unit]    = Sync[F].delay{ recordings.dequeue_ }

  

  /**
    * Evaluates a single network with a dataset.
    */
  def evaluateNet(readoutLayer: FeedForward): ScoredNet = {
    val totalError = recordings.foldLeft(0.0){ case (errorAcc, dataset) =>
      dataset.foldLeft(errorAcc){ case (errorAcc, (initAgent, inputs)) =>
        val agentInputs = inputs.map(readoutLayer.runNet)
        val error = agentInputs.foldLeft((0.0, initAgent)){ case ((errorAcc, agent), input) =>
          val nextAgent = agent.update(input(0), input(1))
          val autopilot = agent.autopilot
          val diff = Math.abs(nextAgent.heading - autopilot.heading)

          // say(s"previous agent was ${agent}")
          // say(s"this agent was     ${nextAgent}")
          // say(s"autopilot agent:   ${autopilot}")
          // say(s"diff: " + "%.4f".format(diff) + "\n\n\n")

          (errorAcc + diff, nextAgent)
        }
        error._1 + errorAcc
      }
    }
    (totalError, 0.0, readoutLayer)
  }


  // private val netsPerGen     = hardcode(20)
  // private val mutantsPerGen  = hardcode(4)
  // private val childrenPerGen = hardcode(4)


  val initNetworks = (0 until conf.ga.generationSize)
    .map(_ => (FFANN.randomNet(conf.readout))).toList

  def evaluateNets(unscoredNetworks: Seq[FeedForward]): Seq[ScoredNet] =
    unscoredNetworks.toList.map(evaluateNet)

  def combineGenerations(prev: Seq[ScoredNet], next: Seq[ScoredNet]): Seq[ScoredNet] =
    (prev ++ next).sortByError


  def start: Stream[F,Unit] = {
    pauseSignal.discrete.tail.changes.flatMap{ _ =>

      def go(evaluatedNetworks: Seq[ScoredNet]): Pull[F,Unit,Unit] = {

        def genNextGen: Seq[FeedForward] = generate(evaluatedNetworks)
        say(evaluatedNetworks.map(_.error).map(x => "%.2f".format(x)).mkString(", "))

        val rescored: Pull[F,Unit,Seq[ScoredNet]] = Pull.eval(updateSignal.modify(x => (false, x))).map{ rescore =>
          if(rescore) {
            say("rescoring")
            evaluateNets(evaluatedNetworks.strip)
          }
          else
            evaluatedNetworks
        }

        def updateBest(best: ScoredNet) = {
          Pull.eval(bestResult.get.map(_._1)).flatMap{ topScore =>
          if(best.error < topScore) {
            say(s"new top performer with error ${best.error}")
            say(s"network: ${best.net}")
            Pull.eval(bestResult.set((best.error, FFANN.ffPipe(best.net))))
          }
          else
            Pull.pure(())
          }
        }

        for {
          scored        <- rescored
          nextGen        = evaluateNets(genNextGen)
          nextPop        = combineGenerations(scored, nextGen).toList
          worst          = nextPop.sortByError.head
          best           = nextPop.sortByError.last
          _             <- updateBest(best)
          _             <- Pull.eval(Fsay[F](s"One generation done. Best performer had error ${best.error}, worst had ${worst.error}"))
          (drop, keep)   = nextPop.splitAt(conf.ga.dropPerGen)
          (dropS, keepS) = nextPop.map(x => "%.2f, ".format(x.error)).splitAt(conf.ga.dropPerGen)
          _              = say(s"keeping errors ${keepS.mkString}")
          _              = say(s"dropping errors ${dropS.mkString}")
          _             <- go(keep)
        } yield ()
      }

      def init: Pull[F,Unit,Unit] = {
        val scored = evaluateNets(initNetworks)
        go(scored.sortByError)
      }

      init.stream
    }
  }
}


object ReadoutOptimizer {
  def apply[F[_]: Concurrent](
    conf               : FullSettings,
    simRunnerEvaluator : Pipe[F,Chunk[Double],Double],
    bestResult         : SignallingRef[F,(Double, Pipe[F,Chunk[Double],Chunk[Double]])]): Stream[F,ReadoutOptimizer[F]] = {

    Stream.eval(SignallingRef[F,Boolean](true)).flatMap{ sig =>
      Stream.eval(SignallingRef[F,Boolean](true)).map{ update =>
        new ReadoutOptimizer[F](conf, simRunnerEvaluator, bestResult, sig, update)
      }
    }
  }
}
