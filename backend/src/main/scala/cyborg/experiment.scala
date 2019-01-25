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
 Reservoir    |   ┌-----┐   ┌-------┐    |
  +-----+  ┌--┼-->|Input|-->| RO    |<-┐ |
  |     |--┘  |   └-----┘   └-----┬-┘  | |
  |     |     |                   |    | |
  |     |     |                   v    | |
  |     |<-┐  |   ┌-------┐    ┌-----┐ | |
  +-----+  └--┼---|Perturb|<-┬-|Task | | |
              |   └-------┘  | └-----┘ | |
              |              └---------┘ |
              +--------------------------+

  Depicted in the drawing: Reservoir input is filtered (Input) then interpreted (RO aka readout layer)
  and is used as input for the task runner.

  The task state is then used as a perturbation, but importantly also used to select the next RO

  This topology corresponds to the agent task
  */
class ClosedLoopExperiment[F[_]: Concurrent,FilterO,ReadoutO,TaskO,PerturbationO] {

  type InputFilter           = List[Topic[F,TaggedSegment]] => Stream[F,FilterO]
  type ReadoutLayer          = Pipe[F,FilterO,ReadoutO]
  type TaskRunner            = Pipe[F,ReadoutO,TaskO]
  type PerturbationTransform = Pipe[F,TaskO,PerturbationO]

  def run(
    taskRunner            : TaskRunner,
    perturbationTransform : PerturbationTransform,
    readoutSource         : Stream[F,ReadoutLayer],
    evaluationSink        : Sink[F,TaskO],
    perturbationSink      : Sink[F,PerturbationO]): Sink[F,FilterO] = { reservoirOutput =>

    /**
      Creates a pipe for a single experiment.
      After experiment conclusion the pipe must terminate.
      This must be encoded in the `taskRunner` pipe (if not the evaluationSink
      will not know that the stream is terminated)
      */
    def experimentRunner: Pipe[F,FilterO, Stream[F,Unit]] = { inStream =>

      def runOnce(inputStream: Stream[F,FilterO]): Pull[F,Stream[F,Unit],Unit] = {

        readoutSource.pull.uncons1 flatMap {
          case Some((readoutLayer,tl)) => {
            val experiment: Stream[F,Unit] = inputStream
              .through(readoutLayer)
              .through(taskRunner)
              .observe(evaluationSink)
              .through(perturbationTransform)
              .to(perturbationSink)

            // Terminate after one run
            Pull.output1(experiment) >>
              Pull.eval(Fsay("Experiment completed")) >>
              Pull.done
          }
          case None => {
            say("Just shit myself")
            Pull.done
          }
        }
      }

      runOnce(inStream).stream
    }

    // Sequentially run experiment streams
    reservoirOutput.through(repeatPipe(experimentRunner)).flatten
  }
}
