package cyborg

import scala.concurrent.duration._
import params._
import fs2._
import fs2.concurrent.Signal
import cats.effect._
import utilz._
import backendImplicits._
import RBN._
import RBNStreams._

object RBNStreams {
  implicit class RBNStreamExtender(val rbn: RBN) {
    /**
      * Output state of an RBN node as a stream for SHODAN backend to
      * interpret. For now there are only two different states, giving
      * two different spiking behaviours.
      */
    def outputNodeState[F[_]: Concurrent : Timer](node: Node, samplerate: Int,
      resolution: FiniteDuration = 0.05.second, throttle: Boolean = true)
        : Stream[F, Int] = {
      val spikeFreq = if (rbn.state(node)) rbn.highFreq else rbn.lowFreq
      val spikeDistance = samplerate / spikeFreq
      val amplitude = 500.0

      // Create a uniform stream that creates sawtooth waves
      // according to distance to next spike. This is just a
      // placeholder for a real signal for now. The amplitude is
      // arbitrary.
      val output = Stream.range(0, samplerate).map(
        s => ((s % spikeDistance / spikeDistance) * amplitude).toInt
      ).repeat.covary[F]

      if (throttle)
        output.through(utilz.throttlerPipe[F, Int](samplerate, resolution))
      else
        output
    }

    /**
      * Output the state of an RBN node, interruptible by a
      * Signal. Meant to be expanded to do useful stuff in the future.
      */
    def outputNodeStateInterruptible[F[_]: Concurrent : Timer](node: Node, samplerate: Int,
      interrupter: Signal[F, Boolean], resolution: FiniteDuration = 0.05.second,
      throttle: Boolean = true): Stream[F, Int] = {
      rbn.outputNodeState(node, samplerate, resolution = resolution, throttle = throttle)
        .interruptWhen(interrupter)
    }

    /**
      * Interleaves output from states into a new stream
      * _deterministically_. Keeps the outputs chunked,
      * mostly for debugging purposes for now.
      */
    def interleaveNodeStates[F[_]: Concurrent : Timer](samplerate: Int, segmentLength: Int,
      resolution: FiniteDuration = 0.05.seconds): Stream[F, Chunk[Int]] = {
      val mempty = Stream(Chunk[Int]()).covary[F].repeat
      val streams = for (i <- 0 until rbn.state.length)
      yield rbn.outputNodeState(i, samplerate, resolution, throttle = false)
        .chunkN(segmentLength, true)

      // Why is there no concat on chunk??
      streams.foldRight(mempty){(s, acc) => s.zipWith(acc){ case(x,y) => Chunk.concatInts(List(x,y))}}
    }

    /**
      * Output the state of the entire RBN reservoir as one would
      * expect from an MEA.
      */
    def outputState[F[_]: Concurrent : Timer](samplerate: Int, segmentLength: Int,
      resolution: FiniteDuration = 0.05.seconds, throttle: Boolean)
        : Stream[F, Int] = {
      val output =
        interleaveNodeStates(samplerate, segmentLength, resolution)
          .through(utilz.chunkify)

      if (throttle)
        output.through(utilz.throttlerPipe[F, Int](samplerate*rbn.state.length, resolution))
      else
        output
    }
  }
}
