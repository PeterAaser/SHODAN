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
class ReadoutOptimizer[F[_]: Concurrent](
  conf               : FullSettings,
  simRunnerEvaluator : Pipe[F,Chunk[Double],Double],
  bestResult         : SignallingRef[F,Pipe[F,Chunk[Double],Chunk[Double]]],
  pauseSignal        : SignallingRef[F,Boolean],
  updateSignal       : SignallingRef[F,Boolean]
) {

  type FilterOutput  = Chunk[Double]
  type ReadoutOutput = Chunk[Double] // aka (Double, Double)

  // A record of spikes
  type DataSet = Chunk[FilterOutput]

  val recordings = new scala.collection.mutable.Queue[DataSet]()
  def enq(rec : DataSet): F[Unit] = {
    Sync[F].delay{ recordings.enqueue(rec); if(recordings.size > 6) recordings.dequeue_ } >>
    Fsay[F]("enqueued!") >>
    Fsay[F](s"${recordings.size}") >>
    pauseSignal.set(false) >>
    updateSignal.set(true)
  }

  val deq               : F[DataSet] = Sync[F].delay{ recordings.dequeue }
  val deq_              : F[Unit]    = Sync[F].delay{ recordings.dequeue_ }

  

  /**
    * Evaluates a single network with a dataset.
    */
  // Is stream fast enough? Without rechunking it's pretty much the same as no stream, so yes.
  // Additionally it enables more chunking, might be better memorywise.
  def evaluate(dataset: DataSet, readoutLayer: Pipe[F,FilterOutput,ReadoutOutput]): F[Double] = {
    Stream.chunk(dataset).covary[F]
      .through(readoutLayer)
      .through(simRunnerEvaluator)
      .compile
      .foldMonoid
  }


  /**
    * Upon terminating the recorded dataset is inserted into the mutable queue
    */
  // TODO: Not used. The reason is that observe and termination is difficult!
  // In MazeOnRails.run there was an observe where there is now an append.
  val spikeSink: Pipe[F,FilterOutput,Unit] = { inStream =>
    val huh = inStream.map(_.map(_.toDouble)).compile.toChunk.flatMap(enq)
    Stream.eval(huh) >> Ssay[F]("Okay, that's a dataset received!")
  }


  private val netsPerGen     = hardcode(20)
  private val mutantsPerGen  = hardcode(4)
  private val childrenPerGen = hardcode(4)

  def start: Stream[F,Unit] = {
    pauseSignal.discrete.tail.changes.flatMap{ _ =>
      

      val ga = new GA(conf)
      import ga._

      val initNetworks = (0 until netsPerGen)
        .map(_ => (FFANN.randomNet(conf.readout)))
        .toArray

      def scoreNets(unscoredNetworks: Array[FeedForward]): F[List[(Double, FeedForward)]] = unscoredNetworks.toList.map{ network =>
        recordings.map{ recording =>
          evaluate(recording, FFANN.ffPipe(network))
        }.toList
          .sequence
          .map(_.combineAll)
          .map((_, network))
          // .map(x => (scala.util.Random.nextDouble, network))
      }.sequence

      def combineGenerations(prev: Array[(Double, FeedForward)], next: List[(Double, FeedForward)]): Array[(Double, FeedForward)] =
        (prev ++ next).sortWith(_._1 < _._1)

      def go(scoredNetworks: Array[(Double, FeedForward)], topScore: Double): Pull[F,Unit,Unit] = {

        def nextGen = generate(scoredNetworks.clone)

        val rescored = for {
          rescore <- Pull.eval(updateSignal.modify(x => (false, x)))
          lastGen <- if(rescore)
                       Pull.eval(scoreNets(scoredNetworks.map(_._2)))
                     else
                       Pull.pure(scoredNetworks)
        } yield lastGen

        def updateBest(best: (Double, FeedForward)) = {
          if(best._1 > topScore) {
            say(s"new top performer at ${best._1}")
            Pull.eval(bestResult.set(FFANN.ffPipe(best._2)))
          }
          else
            Pull.pure(())
        }

        for {
          scored  <- rescored
          nextGen <- Pull.eval(scoreNets(nextGen)).map(combineGenerations(scoredNetworks, _).toList)
          best     = nextGen.last
          _       <- updateBest(best)
          _       <- go(nextGen.drop(conf.ga.dropPerGen).toArray, math.max(best._1, topScore))
        } yield ()
      }

      def init: Pull[F,Unit,Unit] = {
        for {
          scored <- Pull.eval(scoreNets(initNetworks))
          sorted  = scored.sortWith(_._1 > _._1)
          _      <- go(scored.toArray, scored.last._1)
        } yield ()
      }

      init.stream
    }
  }
}


object ReadoutOptimizer {
  def apply[F[_]: Concurrent](
    conf               : FullSettings,
    simRunnerEvaluator : Pipe[F,Chunk[Double],Double],
    bestResult         : SignallingRef[F,Pipe[F,Chunk[Double],Chunk[Double]]]): Stream[F,ReadoutOptimizer[F]] = {

    Stream.eval(SignallingRef[F,Boolean](true)).flatMap{ sig =>
      Stream.eval(SignallingRef[F,Boolean](true)).map{ update =>
        new ReadoutOptimizer[F](conf, simRunnerEvaluator, bestResult, sig, update)
      }
    }
  }
}
