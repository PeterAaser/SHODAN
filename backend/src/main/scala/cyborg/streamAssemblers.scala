package cyborg

import cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.Topic
import _root_.io.udash.rpc.ClientId
import scala.language.higherKinds
import utilz._
import cats.effect.IO
import cats.effect._
import fs2.async._
import fs2.async.mutable.Topic
import cats.implicits._

// import cyborg.backend.server.ApplicationServer._

object Assemblers {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  /**
    Assembles the necessary components to start SHODAN and then starts up the websocket and
    http server
    TODO: Maybe use a topic for raw data, might be bad due to information being lost before subbing?
    */
  def startSHODAN(implicit ec: EC): Stream[IO, Unit] = {
    // val meameFeedbackSink: Sink[IO,List[Double]] = DspComms.stimuliRequestSink(100)
    val meameFeedbackSink: Sink[IO,List[Double]] = _.drain
    val s = for {
      commandQueue   <- Stream.eval(fs2.async.unboundedQueue[IO,ControlTokens.UserCommand])
      agentQueue     <- Stream.eval(fs2.async.unboundedQueue[IO,Agent])
      topics         <- assembleTopics.through(vectorizeList(60))
      taggedSeqTopic <- Stream.eval(fs2.async.topic[IO,TaggedSegment](TaggedSegment(-1, Vector[Int]())))

      waveformListeners <- Stream.eval(fs2.async.Ref[IO,List[ClientId]](List[ClientId]()))
      agentListeners    <- Stream.eval(fs2.async.Ref[IO,List[ClientId]](List[ClientId]()))

      rpcServer            = Stream.eval(cyborg.backend.server.ApplicationServer.assembleFrontend(
                                           commandQueue,
                                           agentQueue.dequeue,
                                           taggedSeqTopic,
                                           agentListeners,
                                           waveformListeners))

      agentSink            = agentQueue.enqueue
      commandPipe          = staging.commandPipe(topics, taggedSeqTopic, meameFeedbackSink, agentSink)

      frontend       <- rpcServer
      _              <- Stream.eval(frontend.start)

      _ <- commandQueue.dequeue.through(commandPipe).concurrently(networkIO.channelServer(topics).through(_.map(Stream.eval)).joinUnbounded)
    } yield ()
    s.handleErrorWith(z => {say(z); throw z})
  }


  /**
    Reads relevant neuro data from a topic list and pipes each channel through a spike
    detector, before aggregating spikes from each channel into ANN input vectors
    */
  def assembleInputFilter(
    broadcastSource: List[Topic[IO,TaggedSegment]],
    channels: List[Channel],
    spikeDetector: Pipe[IO,Int,Double]
  )(implicit ec: EC): Stream[IO,ffANNinput] = {

    // selects relevant topics and subscribe to them
    val inputTopics = (channels).toList.map(broadcastSource(_))
    val channelStreams = inputTopics.map(_.subscribe(10000))

    // def busyWork: Sink[IO,Int]

    // TODO Does not synchronize streams
    // This means, if at subscription time, one channel has newer input,
    // this discrepancy will never be resolved and one stream will be permanently ahead
    val spikeChannels = channelStreams
      .map(_.map(_.data)
             .through(chunkify)
             .through(spikeDetector))

    roundRobinL(spikeChannels).covary[IO].map(_.toVector)
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

    val interrupted = signalOf[IO,Boolean](false)

    def publishSink(topics: List[Topic[IO,TaggedSegment]]): Sink[IO,TaggedSegment] = {
      val topicsV = topics.toVector
      def go(s: Stream[IO,TaggedSegment]): Pull[IO,Unit,Unit] = {
        s.pull.uncons1 flatMap {

          case Some((taggedSeg, tl)) => {
            val idx = taggedSeg.channel
            if(idx != -1){
              Pull.eval(topicsV(idx).publish1(taggedSeg)) >> go(tl)
            }
            else go(tl)
          }
          case None => Pull.done
        }
      }

      in => go(in).stream
    }


    interrupted.map { interruptSignal =>
      InterruptableAction(
        interruptSignal.set(true),
        source
          .interruptWhen(interruptSignal
                           .discrete
                           .through(logEveryNth(1, z => say(s"topics interrept signal was set to $z"))))
          .observe(rawSink)
          .through(publishSink(topics))
          .compile.drain
      )
    }
  }


  /**
    Simply creates a stream with the db/meame topics. Assumes 60 channels, not easily
    parametrized with the import params thing because db might have more or less channels
    TODO: Outdated, slated for removal. (just call directly?)
    */
  def assembleTopics(implicit ec: EC): Stream[IO,Topic[IO,TaggedSegment]] = {
    Stream.repeatEval(fs2.async.topic[IO,TaggedSegment](TaggedSegment(-1,Vector[Int]())))
  }


  /**
    Assembles a GA run from an input topic and returns a byte stream to MEAME
    */
  def assembleGA(
    dataSource: List[Topic[IO,TaggedSegment]],
    inputChannels: List[Channel],
    frontendAgentObserver: Sink[IO,Agent],
    feedbackSink: Sink[IO,List[Double]])(implicit ec: EC): IO[InterruptableAction[IO]] = {

    import params.experiment._
    import params.filtering._

    def filter = spikeDetector.spikeDetectorPipe[IO](samplerate, MAGIC_THRESHOLD)
    def inputSpikes = assembleInputFilter(dataSource, inputChannels, filter)

    val experimentPipe: Pipe[IO, Vector[Double], Agent] = GArunner.gaPipe

    signalOf[IO,Boolean](false).map { interruptSignal =>
      val interruptTask = for {
        _ <- interruptSignal.set(true)
      } yield ()

      val runTask = inputSpikes
        .interruptWhen(interruptSignal
                         .discrete
                         .through(logEveryNth(1, z => say(s"GA interrept signal was $z"))))
        .through(experimentPipe)
        .observe(frontendAgentObserver)
        .through(_.map((λ: Agent) => {λ.distances}))
        .through(feedbackSink)
        .compile.drain

      InterruptableAction(
        interruptTask,
        runTask
      )
    }
  }


  /**
    Takes in the raw dataStream before separating to topics for perf reasons
    */
  // def assembleWebsocketVisualizer(rawInputStream: Stream[IO, Int]): IO[Server[IO]] = {

  //   val filtered = rawInputStream
  //     .through(mapN(params.waveformVisualizer.blockSize, _.force.toArray.head)) // downsample
  //     .through(mapN(params.waveformVisualizer.wfMsgSize, _.force.toArray))

  //   val server = webSocketServer.webSocketWaveformServer(filtered)
  //   server
  // }


  def assembleMcsFileReader(implicit ec: EC): Stream[IO, Unit] =
   mcsParser.processRecordings

}
