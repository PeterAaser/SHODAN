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

import cyborg.feedback._

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

  val mazeRunner = new Maze(conf)

  def runMazeRunner : Pipe[F,Chunk[Double],Agent]  = mazeRunner.taskRunner

  def run: Stream[F,Unit] = {
      Stream.eval(OnlineOptimizer.GA(conf)).flatMap{ optimizer =>

        /**
          * Runs a reservoir run, returning the next optimizer
          */
        def runOnce = for {
          dataset <- optimizer.bestResult.get.flatMap{ readout =>
            say("Picked up the best result so far")
            say(readout)
            mazeRunner.run(
              spikeStream,
              FFANN.ffPipe(readout.phenotype),
              perturbationSink
            )
          }
          _ <- optimizer.updateDataset(dataset)
          _ <- optimizer.bestResult.update(r => r.copy(error = r.error + 0.1, score = r.score -0.1))
        } yield ()

        Stream.eval(runOnce).repeat
          .concurrently(optimizer.start)
      }
    }
  }
