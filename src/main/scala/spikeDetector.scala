package SHODAN

import fs2._
import fs2.io.file._
import java.nio.file._

import simulacrum._

object spikeDetector {

  import utilz._

  def singleSpikeDetectorPipe[F[_]](f: Vector[Int] => Boolean): Pipe[F,Vector[Int],Boolean] = {
    def go: Handle[F,Vector[Int]] => Pull[F,Boolean,Unit] = h => {
      h.receive1 { case (a, h) =>
        Pull.output1(f(a)) >> go(h)
      }
    }
    _ pull go
  }

  def spikeDetectorPipe[F[_]]
    ( sampleRate: Int
    , detector: Vector[Int] => Boolean
    , meme: Int
    , threshold: Int

    ):Pipe[F, Vector[Int], Boolean] =
  {

    ???

  }
}
