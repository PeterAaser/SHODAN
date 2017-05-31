package com.cyborg

import com.cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.Queue
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

    // val channels = experimentConf.getIntList("DAQchannels").toArray.toList
    // val pointsPerSweep = experimentConf.getInt("sweepSize")

    // hardcoded
    val MAGIC_PERIOD = 1000
    val MAGIC_SAMPLERATE = 1000
    val MAGIC_THRESHOLD = 1000
    val pointsPerSweep = 1000

    val spikeDetectorPipe: Pipe[F, Int, Int] =
      spikeDetector.spikeDetectorPipe(MAGIC_PERIOD, MAGIC_SAMPLERATE, MAGIC_THRESHOLD)

    val neuronChannelsStream: Stream[F,Vector[Stream[F,Int]]] =
      alternator(neuroStream, pointsPerSweep, channels.length, 10000)


    val spikeStream = neuronChannelsStream flatMap {
      streams: Vector[Stream [F,Int]] => {

        println(channels)
        println(channels.length)
        println(streams)
        println(streams.length)
        val spikeChannels: Vector[Stream[F, Int]] = streams.toVector
          .map((λ: Stream[F,Int]) => λ.through(spikeDetectorPipe))

        // val droppedChannels = (streams.toSet -- spikeChannels.toSet).toVector
        // val droppedChannels2 = Stream.emits(droppedChannels.map(_.drain))
        // val droppedChannels3 = droppedChannels2.flatMap(λ => λ)

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
