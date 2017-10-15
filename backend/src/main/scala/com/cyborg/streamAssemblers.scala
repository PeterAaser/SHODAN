package com.cyborg

import com.cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.Topic
import java.io.File
import org.http4s.server.Server
import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import utilz._
import cats.effect.IO
import cats.effect.Effect

object Assemblers {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]


  /**
    Assembles the necessary components to start SHODAN and then starts up the websocket and
    http server
    */
  def startSHODAN(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    val commandQueueS = Stream.eval(fs2.async.unboundedQueue[IO,HttpCommands.UserCommand])
    val agentQueueS = Stream.eval(fs2.async.unboundedQueue[IO,Agent])
    val topicsS = assembleTopics[IO]
    val debugQueueS = Stream.eval(fs2.async.unboundedQueue[IO,DebugMessages.DebugMessage])
    val meameFeedbackSink: Sink[IO,Byte] = _.drain

    import DebugMessages._
    val msg = ChannelTraffic(10, 10)

    val durp: Stream[IO,Unit] = for {
      commandQueue <- commandQueueS
      agentQueue   <- agentQueueS
      topics       <- topicsS
      debugQueue   <- debugQueueS

      httpServer              = Stream.eval(HttpServer.SHODANserver(commandQueue.enqueue, debugQueue))
      webSocketAgentServer    = Stream.eval(webSocketServer.webSocketAgentServer(agentQueue.dequeue))
      webSocketVizServer      = Stream.eval(assembleWebsocketVisualizer(topics))
      agentSink               = agentQueue.enqueue
      commandPipe             = staging.commandPipe(topics, agentSink, meameFeedbackSink)
      channelZeroListener     = topics.head.subscribe(100).through(attachDebugChannel(msg, 10, debugQueue.enqueue)).drain

      server    <- httpServer
      wsServer  <- webSocketAgentServer
      vizServer <- webSocketVizServer
      _         <- commandQueue.dequeue.through(commandPipe).join(100).concurrently(channelZeroListener)

    } yield ()
    durp
  }


  /**
    Reads relevant neuro data from a topic list and pipes each channel through a spike
    detector, before aggregating spikes from each channel into ANN input vectors
    */
  // TODO rename to spike detector something?
  def assembleInputFilter[F[_]: Effect](
    broadcastSource: List[DataTopic[F]],
    channels: List[Channel],
    spikeDetector: Pipe[F,Int,Double]
  ): Stream[F,ffANNinput] = {

    // selects relevant topics and subscribe to them
    val inputTopics = channels.map(broadcastSource(_))
    val channelStreams = inputTopics.map(_.subscribe(100))

    // TODO Does not synchronize streams
    // This means, if at subscription time, one channel has newer input,
    // this discrepancy will never be resolved and one stream will be permanently ahead
    val spikeChannels = channelStreams
      .map(_.map(_._1))
      .map(_.through(chunkify))
      .map(_.through(spikeDetector))

    Stream.emit(spikeChannels).covary[F].through(roundRobin).map(_.toVector)
  }


  /**
    Takes a multiplexed dataSource and a list of topics.
    Demultiplexes the data and publishes data to all channel topics.
    */
  def broadcastDataStream(
    source: Stream[IO,Int],
    topics: List[DataTopic[IO]])(implicit ec: ExecutionContext): Stream[IO,Unit] = {

    import params.experiment._

    def publishSink(topics: List[DataTopic[IO]]): Sink[IO,Int] = {
      def loop(segmentId: Int, topics: List[DataTopic[IO]], s: Stream[IO,Int]): Pull[IO,IO[Unit],Unit] = {
        s.pull.unconsN(segmentLength*totalChannels.toLong, false) flatMap {
          case Some((seg, tl)) => {
            val grouped = seg.toVector.grouped(segmentLength)
            val stamped = grouped.map((_,segmentId)).toList
            val broadCasts = stamped.zip(topics).map{
              case(segment, topic) => topic.publish1(segment)
            }

            Pull.output(Segment.seq(broadCasts)) >> loop(segmentId + 1, topics, tl)
          }
          case None => {
            println(Console.RED + "Uh oh, broadcast datastream None pull" + Console.RESET)
            Pull.done
          }
        }
      }
      in => loop(0, topics, in).stream.map(Stream.eval).join(totalChannels)
    }

    // Should ideally emit a list of topics doing their thing
    source.through(publishSink(topics))
  }


  /**
    Simply creates a stream with the db/meame topics. Assumes 60 channels, not easily
    parametrized with the import params thing because db might have more or less channels
    TODO: Outdatet, slated for removal
    */
  def assembleTopics[F[_]: Effect](implicit ec: ExecutionContext): Stream[F,MeameDataTopic[F]] = {

    // hardcoded
    createTopics(60, (Vector.empty[Int],-1))
  }


  /**
    Assembles a GA run from an input topic and returns a byte stream to MEAME
    */
  def assembleGA[F[_]: Effect](
    dataSource: List[DataTopic[F]],
    inputChannels: List[Channel],
    outputChannels: List[Channel],
    frontendAgentObserver: Sink[F,Agent],
    feedbackSink: Sink[F,Byte])(implicit ec: ExecutionContext): Stream[F,Unit] =
  {

    import params.experiment._
    import params.filtering._

    def filter = spikeDetector.spikeDetectorPipe[F](samplerate, MAGIC_THRESHOLD)

    def inputSpikes = assembleInputFilter(dataSource, inputChannels, filter)

    val toStimFrequencyTransform: List[Double] => String = {
      val logScaler = MEAMEutilz.logScaleBuilder(scala.math.E)
      MEAMEutilz.toStimFrequency(outputChannels, logScaler)
    }

    val experimentPipe = GApipes.experimentPipe(inputSpikes, params.filtering.layout)

    experimentPipe
      .observeAsync(10000)(frontendAgentObserver)
      .through(_.map((λ: Agent) => {λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      .through(text.utf8Encode)
      .through(feedbackSink)
  }


  /**
    Takes data from the DataTopic list, filters the data, vectorizes it to fit message size
    before demuxing it to a single stream which is sent to the visualizer
    */
  // TODO where should data filtering really be handled?
  def assembleWebsocketVisualizer(
    dataSource: List[DataTopic[IO]])(implicit ec: ExecutionContext): IO[Server[IO]] = {

    // val inputSource = assembleWebsocketVisualizerFilter[IO](dataSource)

    val server = webSocketServer.webSocketWaveformServer(assembleWebsocketVisualizerFilter(dataSource))
    server
  }

  def assembleWebsocketVisualizerFilter[F[_]: Effect](
    broadCastSource: List[DataTopic[F]])(implicit ec: ExecutionContext): Stream[F,Array[Int]] = {


    // TODO: Currently ignores segments etc
    import params.waveformVisualizer.wfMsgSize
    import params.waveformVisualizer.blockSize
    import backendImplicits.Sch



    // val huh = Stream.emit(mapped).covary[F].through(roundRobin).through(chunkify)

    import scala.concurrent.duration._
    // val channelStreams = broadCastSource.map(_.subscribe(1000))
    // // val channelStreams = (0 until 60).map(_ => throttul[F](0.002.second).map(_ => (Vector.fill(100)(100), 0))).toList
    // val mapped = channelStreams
    //   .map(_.map(_._1))
    //   .map(_.through(chunkify))
    //   .map(_.through(simpleDownSample(10)))
    //   .map(_.through(mapN(100, _.toArray)))

    // roundRobinL(mapped).through(chunkify)
    val dur = broadCastSource(0).subscribe(100).through(_.map(λ => λ._1)).through(downSamplePipe(2)).through(_.map(_.toArray))
    dur
  }


  def assembleMcsFileReader(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    val theThing = mcsParser.eatDirectory(new File("/home/peteraa/Fuckton_of_MEA_data/hfd5_test").toPath())
    theThing
  }
}

