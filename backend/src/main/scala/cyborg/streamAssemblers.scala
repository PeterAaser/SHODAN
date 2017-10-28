package cyborg

import cyborg.wallAvoid.Agent
import fs2._
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
    TODO: Maybe use a topic for raw data, might be bad due to information being lost before subbing?
    */
  def startSHODAN(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    val commandQueueS = Stream.eval(fs2.async.unboundedQueue[IO,HttpCommands.UserCommand])
    val agentQueueS = Stream.eval(fs2.async.unboundedQueue[IO,Agent])
    val topicsS = assembleTopics[IO]
    val debugQueueS = Stream.eval(fs2.async.unboundedQueue[IO,DebugMessages.DebugMessage])
    val rawDataQueueS = Stream.eval(fs2.async.unboundedQueue[IO,Int])
    val meameFeedbackSink: Sink[IO,List[Double]] = DspComms.stimuliRequestSink(10)

    import DebugMessages._
    val msg = ChannelTraffic(10, 10)

    val durp: Stream[IO,Unit] = for {
      commandQueue <- commandQueueS
      agentQueue   <- agentQueueS
      topics       <- topicsS
      debugQueue   <- debugQueueS
      rawDataQueue <- rawDataQueueS

      httpServer              = Stream.eval(HttpServer.SHODANserver(commandQueue.enqueue, debugQueue))
      webSocketAgentServer    = Stream.eval(webSocketServer.webSocketAgentServer(agentQueue.dequeue))
      webSocketVizServer      = Stream.eval(assembleWebsocketVisualizer(rawDataQueue.dequeue))
      agentSink               = agentQueue.enqueue
      commandPipe             = staging.commandPipe(topics, agentSink, meameFeedbackSink, rawDataQueue)
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
    // TODO there should be a pipe for this in utilz I think
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
    topics: List[DataTopic[IO]],
    rawSink: Sink[IO,Int],
    segmentLength: Int
  )(implicit ec: ExecutionContext): Stream[IO,Unit] = {

    import params.experiment.totalChannels

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
      // TODO add observeAsync, and move it out of arg position where it doesn't really belong
      in => loop(0, topics, in.observe(rawSink)).stream.map(Stream.eval).join(totalChannels)
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
    feedbackSink: Sink[F,List[Double]])(implicit ec: ExecutionContext): Stream[F,Unit] =
  {

    import params.experiment._
    import params.filtering._

    def filter = spikeDetector.spikeDetectorPipe[F](samplerate, MAGIC_THRESHOLD)

    def inputSpikes = assembleInputFilter(dataSource, inputChannels, filter)

    val experimentPipe = GApipes.experimentPipe(inputSpikes, params.filtering.layout)

    experimentPipe
      .observeAsync(10000)(frontendAgentObserver)
      .through(_.map((λ: Agent) => {λ.distances}))
      .through(feedbackSink)
  }


  /**
    Takes in the raw dataStream before separating to topics for perf reasons
    */
  def assembleWebsocketVisualizer(rawInputStream: Stream[IO, Int]): IO[Server[IO]] = {

    val filtered = rawInputStream
      .through(vectorize(1000))
      .through(chunkify)
      .through(downSamplePipe(params.waveformVisualizer.blockSize))
      .through(mapN(params.waveformVisualizer.wfMsgSize, _.toArray))

    val server = webSocketServer.webSocketWaveformServer(filtered)
    server
  }


  def assembleMcsFileReader(implicit ec: ExecutionContext): Stream[IO, Unit] =
    mcsParser.processRecordings
}

