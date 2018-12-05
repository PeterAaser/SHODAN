package cyborg

import cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.{ Queue, Signal }
import fs2.async.mutable.Topic
import _root_.io.udash.rpc.ClientId
import java.nio.file.Paths
import org.joda.time.Seconds
import scala.language.higherKinds
import cats.effect.IO
import cats.effect._
import fs2.async._
import fs2.async.mutable.Topic
import cats.implicits._

import cyborg.backend.server.ApplicationServer
import cyborg.Setting._
import cyborg.utilz._

import scala.concurrent.duration._

import backendImplicits._
import cats.effect.implicits._
import cats.effect.Timer

object Assemblers {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  /**
    Assembles the necessary components to start SHODAN
    */
  def startSHODAN(implicit ec: EC): Stream[IO, Unit] = {

    val shittyPath = Paths.get("/home/peteraa/memez")
    val shittyEventSink = cyborg.io.files.fileIO.timestampedEventWriter[IO]
    val hurr = shittyEventSink(shittyPath)


    val meameFeedbackSink: Sink[IO,List[Double]] =
      _.through(PerturbationTransform.toStimReq())
        .to(cyborg.dsp.DSP.stimuliRequestSink())

    for {
      conf              <- Stream.eval(assembleConfig)
      getConf           =  conf.get
      staleConf         <- Stream.eval(conf.get)
      mock              <- mockServer.assembleTestHttpServer(params.http.MEAMEclient.port)
      mockEvents        =  mock._2
      _                 <- Ssay[IO]("mock server up")
      state             <- Stream.eval(signalOf[IO,ProgramState](ProgramState()))

      eventQueue        <- Stream.eval(fs2.async.unboundedQueue[IO,String])

      commandQueue      <- Stream.eval(fs2.async.unboundedQueue[IO,UserCommand])
      agentTopic        <- Stream.eval(fs2.async.topic[IO, Agent](Agent.init))
      topics            <- assembleTopics.through(vectorizeList(60))
      taggedSeqTopic    <- Stream.eval(fs2.async.topic[IO,TaggedSegment](TaggedSegment(-1, Vector[Int]())))

      waveformListeners <- Stream.eval(fs2.async.Ref[IO,List[ClientId]](List[ClientId]()))
      agentListeners    <- Stream.eval(fs2.async.Ref[IO,List[ClientId]](List[ClientId]()))

      _                 <- Ssay[IO]("Starting DSP...")
      _                 <- Stream.eval(cyborg.dsp.DSP.setup(staleConf.experimentSettings))
      _                 <- Ssay[IO]("In the pipe, 5 by 5")

      rpcServer         =  Stream.eval(cyborg.backend.server.ApplicationServer.assembleFrontend(
                                     commandQueue,
                                     agentTopic.subscribe(2000),
                                     taggedSeqTopic,
                                     waveformListeners,
                                     agentListeners,
                                     state,
                                     conf))


      commandPipe       <- Stream.eval(staging.commandPipe(
                                      topics,
                                      taggedSeqTopic,
                                      meameFeedbackSink,
                                      agentTopic,
                                      state,
                                      getConf,
                                      eventQueue,
                                      waveformListeners,
                                      agentListeners))

      frontend          <- rpcServer
      _                 <- Stream.eval(frontend.start)

      _                 <- Ssay[IO]("All systems go")
      _                 <- commandQueue.dequeue.through(commandPipe)
                             .concurrently(mockServer.assembleTestTcpServer(params.TCP.port))
                             .concurrently(mockEvents.through(logEveryNth(1, (x: mockDSP.Event) => s"DSP event $x")).drain)
                             .concurrently(agentTopic.subscribe(100).through(_.map(_.toString)).through(eventQueue.enqueue))
                             .concurrently(eventQueue.dequeue.through(hurr))

    } yield ()
  }


  /**
    Reads relevant neuro data from a topic list and pipes each channel through a spike
    detector, before aggregating spikes from each channel into ANN input vectors
    */
  def assembleInputFilter(
    broadcastSource : List[Topic[IO,TaggedSegment]],
    spikeDetector   : Pipe[IO,Int,Double],
    getConf         : IO[FullSettings]
  )(implicit ec     : EC) : IO[Stream[IO,ffANNinput]] = getConf map { conf =>

    val channels = conf.filterSettings.inputChannels

    // selects relevant topics and subscribe to them
    val inputTopics = (channels).toList.map(broadcastSource(_))
    val channelStreams = inputTopics.map(_.subscribe(10000))

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
    */
  def broadcastDataStream(
    source      : Stream[IO,TaggedSegment],
    topics      : List[Topic[IO,TaggedSegment]],
    rawSink     : Sink[IO,TaggedSegment])(implicit ec : EC) : IO[InterruptableAction[IO]] = {

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


  def assembleTopics(implicit ec: EC): Stream[IO,Topic[IO,TaggedSegment]] =
    Stream.repeatEval(fs2.async.topic[IO,TaggedSegment](TaggedSegment(-1,Vector[Int]())))


  /**
    Assembles a GA run from an input topic and returns a byte stream to MEAME and observes
    the agent updates via the frontEndAgentObserver
    */
  def assembleGA(
    dataSource            : List[Topic[IO,TaggedSegment]],
    inputChannels         : List[Channel],
    frontendAgentObserver : Sink[IO,Agent],
    feedbackSink          : Sink[IO,List[Double]],
    eventLogger           : Queue[IO,String],
    getConf               : IO[FullSettings])(implicit ec : EC) : IO[InterruptableAction[IO]] = {

    getConf flatMap { conf =>

      val filter = spikeDetector.spikeDetectorPipe[IO](getConf)
      val inputSpikes = filter.flatMap{filter => assembleInputFilter(dataSource, filter, getConf)}

      val gaRunner = new GArunner(conf.gaSettings, conf.filterSettings)

      val experimentPipe: Pipe[IO, Vector[Double], Agent] = gaRunner.gaPipe(eventLogger)

      signalOf[IO,Boolean](false).map { interruptSignal =>
        val interruptTask = for {
          _ <- interruptSignal.set(true)
        } yield ()

        val runTask = inputSpikes flatMap (
          _.interruptWhen(interruptSignal
                           .discrete
                           .through(logEveryNth(1, z => say(s"GA interrept signal was $z"))))
          .through(experimentPipe)
          .observe(frontendAgentObserver)
          .through(_.map((λ: Agent) => {λ.distances}))
          .through(feedbackSink)
          .compile.drain)

        InterruptableAction(
          interruptTask,
          runTask
        )
      }
    }
  }


  def assembleConfig(implicit ec: EC): IO[Signal[IO, FullSettings]] =
    fs2.async.signalOf[IO,FullSettings](FullSettings.default)


  def assembleMcsFileReader(implicit ec: EC): Stream[IO, Unit] =
   cyborg.io.files.mcsParser.processRecordings
}
