package SHODAN

import fs2._
import fs2.util.Async
import scala.language.higherKinds

object Assemblers {

  def assembleProcess[F[_]:Async]
    ( channels: Int
    , sweepSize: Int
    , samplesPerSpike: Int): Pipe[F,Int,List[Double]] = neuronInputs => {

    import neurIO._
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
        , List(1.0, 2.0, 3.0, 1.0, 2.0)
        , List(1.0, 2.0, 3.0, 1.0, 2.0, 3.0,1.0, 2.0, 3.0, 1.0, 2.0, 3.0))


    val processedSpikes: Stream[F,List[Double]] = {
      val pipe = Filters.ANNPipes.ffPipe[F](10, FF)
      spikePatterns.through(pipe)
    }

    val gamePipe = agentPipe.wallAvoidancePipe[F]
    val gameOutput = processedSpikes.through(gamePipe)
    gameOutput
  }


  def assembleIO[F[_]:Async]
  ( ip: String
  , port: Int
  , reuseAddress: Boolean
  , sendBufferSize: Int
  , receiveBufferSize: Int
  , keepAlive: Boolean
  , noDelay: Boolean
  , process: Pipe[F,Int,List[Double]])
      : (F[Unit]) = {

    import utilz._
    import namedACG._

    import fs2.io.tcp._
    import java.net.InetSocketAddress
    import java.nio.channels.AsynchronousChannelGroup

    neurIO.createClientStream(
          ip
        , port
        , reuseAddress
        , sendBufferSize
        , receiveBufferSize
        , keepAlive
        , noDelay
    ).flatMap
    { λ: Socket[F] => {
       λ.reads(256, None)
         .through(utilz.bytesToInts)
         .through(process)
         .through(utilz.chunkify)
         .through(utilz.doubleToByte)
         .through(λ.writes(None))
     }
    }.run
  }

  def assembleExperiment[F[_]: Async](
    ip: String,
    port: Int,
    reuseAddress: Boolean,
    sendBufferSize: Int,
    receiveBufferSize: Int,
    keepAlive: Boolean,
    noDelay: Boolean,

    sampleRate: Int,
    channels: Int

  ): F[Unit] = {

    // how many bytes of each channel?
    val sweepSize = 64

    // How many samples should we look at to detect a spike?
    val samplesPerSpike = 128

    val neuroProcess: Pipe[F,Int,List[Double]] =
      assembleProcess(channels, sweepSize, samplesPerSpike)

    val experiment = assembleIO(
        ip
      , port
      , reuseAddress
      , sendBufferSize
      , receiveBufferSize
      , keepAlive
      , noDelay
      , neuroProcess
    )
    experiment
  }
}
