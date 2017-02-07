package com.cyborg

import fs2._
import fs2.io.file._
import java.nio.file._

import simulacrum._

import scala.language.higherKinds

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

  def simpleDetector(datapoints: Vector[Int]): Boolean = {
    val voltageThreshold = 10000
    val spikeThreshold = 100
    (datapoints count (math.abs(_) > voltageThreshold)) > spikeThreshold
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
