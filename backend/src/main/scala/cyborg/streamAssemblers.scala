package cyborg

import cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.Topic
import org.http4s.server.Server
import scala.language.higherKinds
import utilz._
import cats.effect.IO
import cats.effect.Effect
import fs2.async._
import fs2.async.mutable.Topic

object Assemblers {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  /**
    Assembles the necessary components to start SHODAN and then starts up the websocket and
    http server
    TODO: Maybe use a topic for raw data, might be bad due to information being lost before subbing?
    */
  def startSHODAN(implicit ec: EC): Stream[IO, Unit] = {
    // val commandQueueS = Stream.eval(fs2.async.unboundedQueue[IO,HttpCommands.UserCommand])
    // val agentQueueS = Stream.eval(fs2.async.unboundedQueue[IO,Agent])
    // val topicsS = assembleTopics[IO]
    // val debugQueueS = Stream.eval(fs2.async.unboundedQueue[IO,DebugMessages.DebugMessage])
    // val taggedSegTopicS = Stream.eval(fs2.async.topic[IO,TaggedSegment](TaggedSegment(-1, Vector[Int]())))

    // magic hardcoded threshold
    val meameFeedbackSink: Sink[IO,List[Double]] = DspComms.stimuliRequestSink(100)

    for {
      commandQueue   <- Stream.eval(fs2.async.unboundedQueue[IO,HttpCommands.UserCommand])
      agentQueue     <- Stream.eval(fs2.async.unboundedQueue[IO,Agent])
      topics         <- assembleTopics[IO]
      debugQueue     <- Stream.eval(fs2.async.unboundedQueue[IO,DebugMessages.DebugMessage])
      taggedSeqTopic <- Stream.eval(fs2.async.topic[IO,TaggedSegment](TaggedSegment(-1, Vector[Int]())))

      httpServer           = Stream.eval(HttpServer.SHODANserver(commandQueue.enqueue, debugQueue))
      webSocketAgentServer = Stream.eval(webSocketServer.webSocketAgentServer(agentQueue.dequeue))
      webSocketVizServer   = Stream.eval(assembleWebsocketVisualizer(taggedSeqTopic.subscribe(10000).through(_.map(_.data._2)).through(chunkify)))

      agentSink            = agentQueue.enqueue
      commandPipe          = staging2.commandPipe(topics, taggedSeqTopic, meameFeedbackSink, agentSink)

      server         <- httpServer
      wsAgentServer  <- webSocketAgentServer
      wsVizServer    <- webSocketVizServer

      _ <- commandQueue.dequeue.through(commandPipe)
    } yield ()

    // commandQueueS flatMap {           commandQueue =>
    //   agentQueueS flatMap {           agentQueue =>
    //     topicsS flatMap {             topics =>
    //       debugQueueS flatMap {       debugQueue =>
    //         taggedSegTopicS flatMap { taggedSeqTopic =>

    //           val httpServer           = Stream.eval(HttpServer.SHODANserver(commandQueue.enqueue, debugQueue))
    //           val webSocketAgentServer = Stream.eval(webSocketServer.webSocketAgentServer(agentQueue.dequeue))
    //           val webSocketVizServer   = Stream.eval(assembleWebsocketVisualizer(taggedSeqTopic.subscribe(10000).through(_.map(_.data._2)).through(chunkify)))

    //           val agentSink            = agentQueue.enqueue
    //           val commandPipe          = staging.commandPipe(topics, agentSink, meameFeedbackSink, taggedSeqTopic)


    //           httpServer flatMap {               server =>
    //             webSocketAgentServer flatMap {   wsAgentServer =>
    //               webSocketVizServer flatMap {   wsVizServer =>

    //                 commandQueue.dequeue.through(commandPipe).map(Stream.eval).join(100)
    //               }
    //             }
    //           }
    //         }
    //       }
    //     }
    //   }
    // }
  }


  /**
    Reads relevant neuro data from a topic list and pipes each channel through a spike
    detector, before aggregating spikes from each channel into ANN input vectors
    */
  def assembleInputFilter(
    broadcastSource: List[Topic[IO,TaggedSegment]],
    channels: List[Channel],
    spikeDetector: Pipe[IO,Int,Double]
  ): Stream[IO,ffANNinput] = {

    // selects relevant topics and subscribe to them
    val inputTopics = channels.map(broadcastSource(_))
    val channelStreams = inputTopics.map(_.subscribe(100))

    println(s"creating input filter with channels $channels")

    // TODO Does not synchronize streams
    // This means, if at subscription time, one channel has newer input,
    // this discrepancy will never be resolved and one stream will be permanently ahead
    val spikeChannels = channelStreams
      .map(_.map(_.data._2)
             .through(chunkify)
             .through(spikeDetector))

    println("checking if clogged")

    Stream.emit(spikeChannels).covary[IO].through(roundRobin).map(_.toVector)
  }


  /**
    Takes a multiplexed dataSource and a list of topics.
    Demultiplexes the data and publishes data to all channel topics.

    Returns a tuple of the stream and a cancel action

    TODO: Should synchronize (e.g at start it should drop until it gets channel 0)
    */
  def broadcastDataStream(
    source: Stream[IO,TaggedSegment],
    topics: List[Topic[IO,TaggedSegment]],
    rawSink: Sink[IO,TaggedSegment]
  )(implicit ec: EC): IO[InterruptableAction[IO]] = {

    import params.experiment.totalChannels

    val interrupted = signalOf[IO,Boolean](false)

    def publishSink(topics: List[Topic[IO,TaggedSegment]]): Sink[IO,TaggedSegment] = {
      val topicsV = topics.toVector
      def go(s: Stream[IO,TaggedSegment]): Pull[IO,IO[Unit],Unit] = {
        // println("publish sink is running")
        s.pull.uncons1 flatMap {
          case Some((taggedSeg, tl)) => {
            val idx = taggedSeg.data._1
            Pull.output1(topicsV(idx).publish1(taggedSeg)) >> go(tl)
          }
        }
      }

      in => go(in).stream.map(Stream.eval).join(totalChannels)
    }

    interrupted.map { interruptSignal =>
      InterruptableAction(
        interruptSignal.set(true),
        source.observe(rawSink).through(publishSink(topics)).interruptWhen(interruptSignal.discrete).run)
    }
  }


  /**
    Simply creates a stream with the db/meame topics. Assumes 60 channels, not easily
    parametrized with the import params thing because db might have more or less channels
    TODO: Outdated, slated for removal. (just call directly?)
    */
  def assembleTopics[F[_]: Effect](implicit ec: EC): Stream[F,List[Topic[F,TaggedSegment]]] = {

    // hardcoded init. Should be harmless
    createTopics[F,TaggedSegment](60, TaggedSegment((-1,Vector[Int]())))
  }


  /**
    Assembles a GA run from an input topic and returns a byte stream to MEAME
    */
  def assembleGA(
    dataSource: List[Topic[IO,TaggedSegment]],
    inputChannels: List[Channel],
    frontendAgentObserver: Sink[IO,Agent],
    feedbackSink: Sink[IO,List[Double]])(implicit ec: EC): Stream[IO,Unit] =
  {

    import params.experiment._
    import params.filtering._

    def filter = spikeDetector.spikeDetectorPipe[IO](samplerate, MAGIC_THRESHOLD)
    def inputSpikes = assembleInputFilter(dataSource, inputChannels, filter)

    val experimentPipe: Pipe[IO, Vector[Double], Agent] = GArunner.gaPipe

    inputSpikes.through(experimentPipe)
      .observeAsync(10000)(frontendAgentObserver)
      .through(_.map((λ: Agent) => {λ.distances}))
      .through(feedbackSink)
  }


  /**
    Takes in the raw dataStream before separating to topics for perf reasons
    */
  def assembleWebsocketVisualizer(rawInputStream: Stream[IO, Int]): IO[Server[IO]] = {

    val filtered = rawInputStream
      .through(mapN(params.waveformVisualizer.blockSize, _.toArray.head)) // downsample
      .through(mapN(params.waveformVisualizer.wfMsgSize, _.toArray))

    val server = webSocketServer.webSocketWaveformServer(filtered)
    server
  }


  def assembleMcsFileReader(implicit ec: EC): Stream[IO, Unit] =
    mcsParser.processRecordings
}
