package com.cyborg

import com.cyborg.params.NeuroDataParams
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
  def assembleInputFilter[F[_]:Async]: Pipe[F, Int, ffANNinput] = neuroStream => {

    val conf = ConfigFactory.load()
    val experimentConf = conf.getConfig("experimentConf")

    val channels = experimentConf.getIntList("DAQchannels").toArray.toList
    val pointsPerSweep = experimentConf.getInt("sweepSize")

    val MAGIC_PERIOD = 1000
    val MAGIC_SAMPLERATE = 1000
    val MAGIC_THRESHOLD = 1000

    val spikeDetectorPipe: Pipe[F, Int, Int] =
      spikeDetector.spikeDetectorPipe(MAGIC_PERIOD, MAGIC_SAMPLERATE, MAGIC_THRESHOLD)


    val neuronChannels: Stream[F,List[Stream[F,Vector[Int]]]] =
      alternate(neuroStream, pointsPerSweep, 256*256*8, channels.length)


    val spikeStream = neuronChannels flatMap {
      channels: List[Stream [F,Vector[Int]]] => {

        val spikeChannels: List[Stream[F, Int]] = channels
          .map((λ: Stream[F,Vector[Int]]) => λ.through(unpacker[F,Int]))
          .map((λ: Stream[F,Int]) => λ.through(spikeDetectorPipe))

        val spikeTrains =
          (Stream[F, Vector[Int]](Vector[Int]()).repeat /: spikeChannels){
            (b: Stream[F, Vector[Int]], a: Stream[F, Int]) => b.zipWith(a){
              (λ, µ) => µ +: λ
            }
          }
        spikeTrains
      }
    }

    spikeStream.through(_.map(_.map(_.toDouble)))
  }


  def assembleAgentPipe[F[_]: Async](ff: Filters.FeedForward[Double]): Pipe[F, ffANNinput, Agent] = ffInput => {
    val FF = Filters.ANNPipes.ffPipe[F](ff)
    val gamePipe = agentPipe.wallAvoidancePipe[F]()

    ffInput.through(FF).through(gamePipe)
  }
}