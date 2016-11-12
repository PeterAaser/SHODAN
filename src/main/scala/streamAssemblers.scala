package SHODAN

import fs2._

object Assemblers {

  def assembleProcess
    ( channels: Int
    , sweepSize: Int
    , samplesPerSpike: Int): Pipe[Task,Int,List[Double]] = neuronInputs => {

    import neurIO._
    import utilz._

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
    gameOutput
  }


  def assembleIO
  ( ip: String
  , port: Int
  , reuseAddress: Boolean
  , sendBufferSize: Int
  , receiveBufferSize: Int
  , keepAlive: Boolean
  , noDelay: Boolean)
      : (Stream[Task,Byte], Sink[Task,Byte]) = {

    import utilz._
    import namedACG._

    import fs2.io.tcp._
    import java.net.InetSocketAddress
    import java.nio.channels.AsynchronousChannelGroup

    implicit val strategy: Strategy = Strategy.fromFixedDaemonPool(8)
    implicit val tcpACG : AsynchronousChannelGroup = namedACG("tcp")
    implicit val scheduler: Scheduler = Scheduler.fromFixedDaemonPool(8)

    val address = new InetSocketAddress(ip, port)

    val clientStream: Stream[Task,Socket[Task]] = client(
      address
        , reuseAddress
        , sendBufferSize
        , receiveBufferSize
        , keepAlive
        , noDelay
    )

    // very sorry. FlatMap must return a stream.
    // There is surely a better way than whatever the fuck this thing is
    var s: Sink[Task,Byte] = {
      def go: Handle[Task,Byte] => Pull[Task,Unit,Unit] = h => {
        h.receive1 { (d, h) => go(h)}}
      _.pull(go)
    }

    (clientStream.flatMap { x => s = x.writes(None); x.reads(256, None)},
     s)

  }

  def assembleExperiment[F[_]](
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

    val (inStream, outStream) =
      assembleIO(ip, port, reuseAddress, sendBufferSize, receiveBufferSize, keepAlive, noDelay)

    // how many bytes of each channel?
    val sweepSize = 64

    // How many samples should we look at to detect a spike?
    val samplesPerSpike = 512

    val neuroProcess: Pipe[Task,Int,List[Double]] =
      assembleProcess(channels, sweepSize, samplesPerSpike)

    ???
  }
}
