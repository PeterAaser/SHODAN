package com.cyborg

import fs2._
import fs2.util.Async
import scala.language.higherKinds
import MEAMEutilz._

object Assemblers {

  def assembleProcess[F[_]:Async]
    ( channels: Int
    , sweepSize: Int
    , samplesPerSpike: Int):
      Pipe[F,Int,List[Double]] = neuronInputs => {

    import utilz._

    val neuronChannels: Stream[F,List[Stream[F,Vector[Int]]]] = alternate(
      neuronInputs,
      sweepSize,
      256*256,
      channels
    )

    val channelWindowerPipe: Pipe[F,Int,Vector[Int]] =
      stridedSlide[F,Int](samplesPerSpike, samplesPerSpike/4)

    val spikeDetectorPipe: Pipe[F,Vector[Int],Boolean] =
      spikeDetector.singleSpikeDetectorPipe(spikeDetector.simpleDetector)

    val spikePatterns: Stream[F, Vector[Boolean]] = neuronChannels.flatMap {
      channels: List[Stream[F,Vector[Int]]] => {

        val windowedChannels: List[Stream[F,Boolean]] = channels
          .map((λ: Stream[F,Vector[Int]]) => λ.through(unpacker[F,Int]))
          .map((λ: Stream[F,Int]) => λ.through(channelWindowerPipe))
          .map((λ: Stream[F,Vector[Int]]) => λ.through(spikeDetectorPipe))

        val spikeTrainsT: Stream[F, Vector[Boolean]] = {
          (Stream[F,Vector[Boolean]](Vector[Boolean]()).repeat /: windowedChannels){
            (b: Stream[F, Vector[Boolean]], a: Stream[F, Boolean]) => b.zipWith(a){
              (λ, µ) => µ +: λ
            }
          }
        }
        spikeTrainsT
      }
    }


    val FF = Filters.FeedForward(
      List(2, 3, 2)
        , List(0.1, 0.2, 0.4, 0.0, 0.3)
        , List(0.1, -0.2, 0.2, -0.1, 2.0, -0.4, 0.1, -0.2, 0.1, 1.0, 0.3, 0.3))


    val processedSpikes: Stream[F,List[Double]] = {
      val pipe = Filters.ANNPipes.ffPipe[F](10, FF)
      spikePatterns.through(pipe)
    }

    val gamePipe = agentPipe.wallAvoidancePipe[F]
    val gameOutput = processedSpikes.through(gamePipe)

    gameOutput
  }

  def assembleExperiment[F[_]: Async](
    meameReadStream: Stream[F, Byte],
    meameWriteSink: Sink[F, Byte],

    sampleRate: Int,
    channels: Int

  ): Stream[F, Unit] = {

    import utilz._
    import namedACG._

    import fs2.io.tcp._
    import java.net.InetSocketAddress
    import java.nio.channels.AsynchronousChannelGroup

    // how many bytes of each channel?
    val sweepSize = 64

    // How many samples should we look at to detect a spike?
    val samplesPerSpike = 128

    val toStimFrequencyTransform: List[Double] => String = {
      val logScaler = logScaleBuilder(scala.math.E)
      toStimFrequency(List(3, 4, 5, 6), logScaler)
    }

    val process: Pipe[F, Int, List[Double]] = assembleProcess(
      channels,
      sweepSize,
      samplesPerSpike)

    val meme =
      meameReadStream
        .through(utilz.bytesToInts)
        .through(process)
        .through(_.map(toStimFrequencyTransform))
        .through(text.utf8Encode)
        .through(meameWriteSink)

    meme

  }
}
