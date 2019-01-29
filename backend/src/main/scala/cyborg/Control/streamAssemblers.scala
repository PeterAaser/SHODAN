package cyborg

import cats.arrow.FunctionK
import cats.data._
import fs2._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.wallAvoid.Agent
import _root_.io.udash.rpc.ClientId
import java.nio.file.Paths
import org.joda.time.Seconds
import scala.language.higherKinds
import cats.effect.IO
import cats.effect._
import cats._
import cats.implicits._

import cyborg.backend.server.ApplicationServer
import cyborg.Settings._
import cyborg.utilz._
import cyborg.State._

import scala.concurrent.duration._

import backendImplicits._
import RPCmessages._

import org.http4s.client.blaze._
import org.http4s.client._


object Assemblers {

  /**
    Assembles the necessary components to start SHODAN
    */
  def startSHODAN(httpClient: MEAMEHttpClient[IO]): Stream[IO, Unit] = {

    val dsp = new cyborg.dsp.DSP(httpClient)

    for {
      stateServer       <- Stream.eval(SignallingRef[IO,ProgramState](ProgramState()))
      configServer      <- Stream.eval(SignallingRef[IO,FullSettings](FullSettings.default))
      listeners         <- Stream.eval(Ref.of[IO,List[ClientId]](List[ClientId]()))
      commandQueue      <- Stream.eval(Queue.unbounded[IO,UserCommand])

      frontend          <- Stream.eval(cyborg.backend.server.ApplicationServer.assembleFrontend(
                                         commandQueue.enqueue,
                                         listeners,
                                         stateServer,
                                         configServer))

      _                 <- Stream.eval(frontend.start)


      commandPipe       <- Stream.eval(ControlPipe.controlPipe(
                                         stateServer,
                                         configServer,
                                         commandQueue,
                                         frontend,
                                         httpClient
                                         ))

      _                 <- Ssay[IO]("###### All systems go ######", Console.GREEN_B)
      _                 <- Ssay[IO]("###### All systems go ######", Console.GREEN_B)
      _                 <- Ssay[IO]("###### All systems go ######", Console.GREEN_B)

      _                 <- commandQueue.dequeue.through(commandPipe)
    } yield ()
  }

  val httpClient = BlazeClientBuilder[IO](ec).resource
  val httpInStreamStream = Stream.eval(BlazeClientBuilder[IO](ec).resource)


  def assembleMazeRunner(broadcastSource : List[Topic[IO,TaggedSegment]],
                         agentTopic      : Topic[IO,Agent],
                         dsp             : cyborg.dsp.DSP[IO])
      : Kleisli[Id,FullSettings,Stream[IO,Unit]] = Kleisli{ conf =>

    val perturbationSink: Sink[IO,List[Double]] =
      _.through(PerturbationTransform.toStimReq())
        .to(dsp.stimuliRequestSink(conf))

    val mazeRunner = Maze.runMazeRunner(broadcastSource, perturbationSink, agentTopic.publish)
    val gogo = mazeRunner(conf)
    gogo: Id[Stream[IO,Unit]]
  }


  /**
    Takes a multiplexed dataSource and a list of topics.
    Demultiplexes the data and publishes data to all channel topics.

    Returns a tuple of the stream and a cancel action

    Kinda getting some second thoughts about this being an interruptable action.
    Shouldn't interruption really happen by terminating the enclosing stream?
    Not set in stone, I'm not even sure how that would look, just thingken

    A thought is, what if the cancellation is lost, then the stream will never
    close until it fails on its own. Not nescessarily a bad thing, just a thought

    TODO: Deliberate on whether this needs to be an InterruptibleAction
    */
  def broadcastDataStream(
    source      : Stream[IO,TaggedSegment],
    topics      : List[Topic[IO,TaggedSegment]],
    rawSink     : Sink[IO,TaggedSegment]) : IO[InterruptableAction[IO]] = {

    val interrupted = SignallingRef[IO,Boolean](false)

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
          .interruptWhen(interruptSignal)
          .observe(rawSink)
          .through(publishSink(topics))
          .compile.drain
      )
    }
  }




  def startLiveBroadcast(topics: List[Topic[IO,TaggedSegment]], rawSink: Sink[IO,TaggedSegment])
      : Kleisli[Id, FullSettings, IO[InterruptableAction[IO]]] = for {
    dataStream <- cyborg.io.Network.streamFromTCP
  } yield broadcastDataStream(dataStream, topics, rawSink)


  /**
    Since a playback from broadcast alters the config it is encoded using StateT. A StateT can be transformed
    to a readerT using `StateT.flatMapToKleisli` defined in the streamUtils extension method for StateT
    */
  def startPlaybackBroadcast(id: Int, topics: List[Topic[IO,TaggedSegment]], rawSink: Sink[IO,TaggedSegment])
      : StateT[IO, FullSettings, InterruptableAction[IO]] = StateT[IO, FullSettings, InterruptableAction[IO]] { conf =>
    val recordingInfoTask: IO[RecordingInfo] = cyborg.io.database.databaseIO.getRecordingInfo(id)
    recordingInfoTask.flatMap { recordingInfo =>
      val newConf = conf.copy(daq = recordingInfo.daqSettings)
      val dataStream = cyborg.io.DB.streamFromDatabase(id)
      val broadcast = broadcastDataStream(dataStream, topics, rawSink)
      broadcast.map(bc => (newConf, bc))
    }
  }


  def assembleTopics: Stream[IO,Topic[IO,TaggedSegment]] =
    Stream.repeatEval(Topic[IO,TaggedSegment](TaggedSegment(-1,Chunk[Int]())))


  val dspEventSink = (s: Stream[IO,mockDSP.Event]) => s.drain
  def assembleMockServer(eventSink: Sink[IO,mockDSP.Event] = dspEventSink): Stream[IO,Unit] =
    (for {
       _ <- Stream.eval(mockServer.assembleTestHttpServer(params.Network.httpPort, dspEventSink))
       _ <- Ssay[IO]("mock server up")
     } yield ()
    ).concurrently(mockServer.assembleTestTcpServer(params.Network.tcpPort))


  /**
    Downsamples a stream into two points per second per pixel

    Kinda silly that we're unable to the time aspect here, but oh well, the frontend
    can deal with that.
    */
  def assembleFrontendDownsampler = Kleisli[Id,FullSettings,Pipe[IO,TaggedSegment,Array[Int]]]{ conf =>

    val targetSegmentLength = (conf.daq.samplerate/conf.daq.segmentLength)

    in => in
      .map(_.downsampleHiLo(conf.daq.segmentLength/targetSegmentLength))
      .chunkify
      .through(mapN(targetSegmentLength*60*2, _.toArray)) // multiply by 2 since we're dealing with max/min
  }


  def startBroadcast(
    configuration: FullSettings,
    programState: ProgramState,
    topics: List[Topic[IO,TaggedSegment]],
    rawSink: Sink[IO,Array[Int]],
    client: MEAMEHttpClient[IO],
  ): IO[InterruptableAction[IO]] = {

    /**
      This must be run first since the kleisli for the DB case is actually a StateT in disguise!
      In english: The BD call alters the configuration, thus to ensure synchronized configs it
      must be used first so that downstream configs match the playback.

      Would be nice to change this I suppose, but it is what it is
      */
    val getAndBroadcastDatastream: Kleisli[IO,FullSettings,InterruptableAction[IO]] = programState.dataSource.get match {
        case Live => for {
          source              <- cyborg.io.Network.streamFromTCP.mapF(IO.pure(_))
          frontendDownsampler <- assembleFrontendDownsampler.mapF(IO.pure(_))
          broadcast           <- Kleisli.liftF(broadcastDataStream(source,
                                                      topics,
                                                      frontendDownsampler andThen rawSink))
        } yield broadcast

        case Playback(id) => cyborg.io.DB.streamFromDatabaseST(id).flatMapToKleisli { source =>
          for {
            frontendDownsampler <- assembleFrontendDownsampler.mapF(IO.pure(_))
            broadcast           <- Kleisli.liftF(broadcastDataStream(source,
                                                        topics,
                                                        frontendDownsampler andThen rawSink))
          } yield broadcast
        }
      }

    // Won't inferr if not broken up. Fun fact: It inferrs correctly with *>
    val startAndBroadcastK = client.startMEAMEserver >> getAndBroadcastDatastream
    startAndBroadcastK(configuration)
  }
}
