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


  Depicted in the drawing: Reservoir input is filtered (Input) then interpreted (RO aka readout layer)
  and is used as input for the task runner.

  The task state is then used as a perturbation, but importantly also used to select the next RO

  This topology corresponds to the agent task
  */
class Experiment[F[_]: Concurrent,
  FilterO,
  ReadoutO,
  TaskO
](taskRunner            : Pipe[F,ReadoutO,TaskO],
  perturbationSink      : Pipe[F,TaskO,Unit],
  dataSetSink           : Pipe[F,FilterO,Unit]
){

  def run(inputStream: Stream[F,FilterO], readoutLayer: Pipe[F,FilterO,ReadoutO], evaluationSink: Pipe[F,TaskO,Unit]): F[Unit] = {
    inputStream
      .observe(dataSetSink)
      .through(readoutLayer)
      .through(taskRunner)
      .observe(evaluationSink)
      .to(perturbationSink)
      .compile
      .drain
  }
}
