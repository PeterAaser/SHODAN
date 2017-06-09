package com.cyborg

import com.cyborg.wallAvoid.Agent
import fs2._
import fs2.util.Async
import scala.language.higherKinds
import com.typesafe.config._
import utilz._

object Assemblers {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  // Needs a sweep size and a spike detector
  def assembleInputFilter[F[_]:Async](channels: List[Int]): Pipe[F, Int, ffANNinput] = neuroStream => {

    val conf = ConfigFactory.load()
    val experimentConf = conf.getConfig("experimentConf")

    import params.filtering._
    import params.experiment._


    val spikeDetectorPipe: Pipe[F, Int, Double] =
      spikeDetector.spikeDetectorPipe(samplerate, MAGIC_THRESHOLD)

    val neuronChannelsStream: Stream[F,Vector[Stream[F,Int]]] =
      alternator(neuroStream, segmentLength, channels.length, 10000)


    val spikeStream = neuronChannelsStream flatMap {
      streams: Vector[Stream [F,Int]] => {

        val spikeChannels: Vector[Stream[F, Double]] = streams.toVector
          .map((λ: Stream[F,Int]) => λ.through(spikeDetectorPipe))

        val spikeTrains =
          (Stream[F, Vector[Double]](Vector[Double]()).repeat /: spikeChannels){
            (b: Stream[F, Vector[Double]], a: Stream[F, Double]) => b.zipWith(a){
              (λ, µ) => µ +: λ
            }
          }
        spikeTrains
      }
    }
    spikeStream
  }


  def assembleAgentPipe[F[_]: Async](ff: Filters.FeedForward): Pipe[F, ffANNinput, Agent] = ffInput => {
    val FF = Filters.ANNPipes.ffPipe[F](ff)
    val gamePipe = agentPipe.wallAvoidancePipe[F]()

    ffInput.through(FF).through(gamePipe)
  }
}
