package SHODAN

import scala.concurrent.duration._

import fs2._
import fs2.io.file._
import java.nio.file._
import simulacrum._
// import cats._, cats.data._, cats.implicits._

import shapeless._
import ops.tuple.FlatMapper
import syntax.std.tuple._

object FsMain {
  def main(args: Array[String]){
    println("nice meme")
  }

  import neurIO._
  import utilz._

  val neuronInputs = neurIO.intStream

  // How many channels are we reading?
  val channels = 4
  // How many inputs are we reading per sweep?
  val sweepSize = 64
  // How many samples do we need to detect a single spike?
  val samplesPerSpike = 512

  val neuronChannels = alternate(neuronInputs, sweepSize, 256*256, channels)

  val channelWindowerPipe: Pipe[Task,Int,Vector[Int]] =
    stridedSlide[Task,Int](samplesPerSpike, samplesPerSpike/4)

  val spikeDetectorPipe: Pipe[Task,Vector[Int],Boolean] =
    spikeDetector.singleSpikeDetectorPipe(_ => true)


  val spikePatterns: Stream[Task, Vector[Boolean]] = neuronChannels.flatMap {
    channels: List[Stream[Task,Vector[Int]]] => {

      val windowedChannels: List[Stream[Task,Boolean]] = channels
        .map((λ: Stream[Task,Vector[Int]]) => λ.through(unpacker[Int]))
        .map((λ: Stream[Task,Int]) => λ.through(channelWindowerPipe))
        .map((λ: Stream[Task,Vector[Int]]) => λ.through(spikeDetectorPipe))

      val spikeTrainsT: Stream[Task, Vector[Boolean]] = {
        (Stream[Task,Vector[Boolean]](Vector[Boolean]()) /: windowedChannels){
          (b: Stream[Task, Vector[Boolean]], a: Stream[Task, Boolean]) => b.zipWith(a){
            (λ, µ) => µ +: λ
          }
        }
      }
      spikeTrainsT
    }
  }


  val FF = Filters.FeedForward(
    List(2, 3, 2)
      , List(1.0, 2.0, 3.0, 1.0, 2.0)
      , List(1.0, 2.0, 3.0, 1.0, 2.0, 3.0,1.0, 2.0, 3.0, 1.0, 2.0, 3.0))


  val processedSpikes: Stream[Task,List[Double]] = {
    val pipe = Filters.ANNPipes.ffPipe[Task](10, FF)
    spikePatterns.through(pipe)
  }

  val gamePipe = agentPipe.wallAvoidancePipe[Task]
  val gameOutput = processedSpikes.through(gamePipe)

  neurIO.explode

}
