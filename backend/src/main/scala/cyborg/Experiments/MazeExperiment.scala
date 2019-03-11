package cyborg

import fs2._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.wallAvoid.Agent
import _root_.io.udash.rpc.ClientId
import java.nio.file.Paths
import org.joda.time.Seconds
import scala.language.higherKinds
import cats.effect.IO
import cats.effect._
import cats.implicits._

import cyborg.backend.server.ApplicationServer
import cyborg.Settings._
import cyborg.utilz._

import scala.concurrent.duration._

import backendImplicits._

import wallAvoid.Agent

/**
  A closed loop experiment has the following topology:

              +--------------------------+
              |                          |
 Reservoir    |   +-----+   +-------+    |
  +-----+  ┌--┼-->|Input|-->| RO    |<---┼-┐
  |     |--┘  |   +-----+   +-----┬-+    | | Readout Optimizer
  |     |     |                   |      | |  +-------+
  |     |     |                   v      | └--|       |
  |     |<-┐  |   +-------+    +-----+   |    |       |    
  +-----+  └--┼---|Perturb|<-┬-|Task |   | ┌->|       |
              |   +-------+  | +-----+   | |  +-------+
              |              └-----------┼-┘ 
              +--------------------------+
  */
class MazeExperiment[F[_]: Concurrent](conf: FullSettings, spikeStream: Stream[F,Chunk[Double]], perturbationSink: Pipe[F,Agent,Unit]) {

  val mazeRunner = new Maze2(conf)

  /**
    Accumulates the diff between an ideal agent and actual agent,
    returning a single element stream.
    */
  def evaluateMazeRunner: Pipe[F,Agent,Double] = { agentStream =>
    agentStream.zipWithPrevious.fold(0.0){
      case (acc, (prevAgent, nextAgent)) => prevAgent.map{ prev =>
        val autopilot = prev.autopilot
        val diff = Math.abs(nextAgent.heading - autopilot.heading)
        // say(s"previous agent was ${prev}")    
        // say(s"this agent was     ${nextAgent}")   
        // say(s"autopilot agent:   ${autopilot}") 
        // say(s"diff: " + "%.4f".format(diff) + "\n\n\n")

        // Ensure skipping agent resets
        if(diff > 2.1*params.game.maxTurnRate){
          // say(s"diff: 0.0")
          acc
        }
        else{
          // say(s"diff: " + "%.4f".format(diff))
          acc + diff
        }
      }.getOrElse(acc)
    }
      // .map{x => say(s"got $x", Console.CYAN); x}
  }

  def runMazeRunner      : Pipe[F,Chunk[Double],Agent]  = mazeRunner.taskRunner
  def simRunnerEvaluator : Pipe[F,Chunk[Double],Double] = runMazeRunner andThen evaluateMazeRunner

  def run: Stream[F,Unit] = {
    Stream.eval(SignallingRef[F,Pipe[F,Chunk[Double], Chunk[Double]]](FFANN.ffPipe(FFANN.randomNet(conf.readout)))).flatMap{ bestResult =>
      ReadoutOptimizer(conf, simRunnerEvaluator, bestResult).flatMap{ mazeOptimizer =>

        def runOne: F[Unit] = {
          bestResult.get.flatMap{ readout =>
            mazeRunner.run(
              spikeStream,
              readout,
              mazeOptimizer.enq(_),
              perturbationSink)
          }
        }

        Stream.eval(runOne).repeat
          .concurrently(mazeOptimizer.start)
      }
    }
  }
}
