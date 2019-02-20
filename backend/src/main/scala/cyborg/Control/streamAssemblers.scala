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

      initState    <- Stream.eval(initProgramState(httpClient, dsp))
      stateServer  <- Stream.eval(SignallingRef[IO,ProgramState](initState))
      configServer <- Stream.eval(SignallingRef[IO,FullSettings](FullSettings.default))
      commandQueue <- Stream.eval(Queue.unbounded[IO,UserCommand])

      frontend     <- Stream.eval(cyborg.backend.server.ApplicationServer.assembleFrontend(
                                         commandQueue,
                                         stateServer,
                                         configServer))

      _            <- Stream.eval(frontend.start)

      commandPipe  <- Stream.eval(ControlPipe.controlPipe(
                                         stateServer,
                                         configServer,
                                         commandQueue,
                                         frontend,
                                         httpClient
                                         ))

      _            <- Ssay[IO]("###### All systems go ######", Console.GREEN_B + Console.WHITE)
      _            <- Ssay[IO]("###### All systems go ######", Console.GREEN_B + Console.WHITE)
      _            <- Ssay[IO]("###### All systems go ######", Console.GREEN_B + Console.WHITE)

      staleConf    <- Stream.eval(configServer.get)
      _            <- Stream.eval(doDspTestStuff(dsp, staleConf))
      _            <- commandQueue.dequeue.through(commandPipe)
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
    rawSink     : Sink[IO,TaggedSegment],
    hurrSink    : Sink[IO,Array[Array[DrawCommand]]],
    filter      : SpikeTools[IO]) : IO[InterruptableAction[IO]] = {

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


    val spammer = filter.visualize(topics(3).subscribe(100000).map(_.data).chunkify).through(hurrSink)

    interrupted.map { interruptSignal =>
      InterruptableAction(
        interruptSignal.set(true),
        source
          .interruptWhen(interruptSignal)
          .observe(rawSink)
          .through(publishSink(topics))
          .concurrently(spammer.drain)
          // .concurrently(spammer2.drain)
          .compile.drain
      )
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


  /**
    The raw sink stuff is a little bolted on due to some design mistakes.
    The problem was a wish to separate recording and data, but I lacked a reference 
    to the stream in the ctrl pipe
    */
  def startBroadcast(
    configuration : FullSettings,
    programState  : ProgramState,
    topics        : List[Topic[IO,TaggedSegment]],
    rawSink       : Sink[IO,TaggedSegment],
    frontendSink  : Sink[IO,Array[Int]],
    hurrSink      : Sink[IO,Array[Array[DrawCommand]]],
    client        : MEAMEHttpClient[IO],
  ): IO[InterruptableAction[IO]] = {

    say("starting broadcast")

    /**
      This must be run first since the kleisli for the DB case is actually a StateT in disguise!
      In english: The BD call alters the configuration, thus to ensure synchronized configs it
      must be used first so that downstream configs match the playback.

      Would be nice to change this I suppose, but it is what it is
      */
    import backendImplicits._

    val getAndBroadcastDatastream: Kleisli[IO,FullSettings,InterruptableAction[IO]] = programState.dataSource.get match {
        case Live => for {
          source              <- cyborg.io.Network.streamFromTCP.mapF(IO.pure(_))
          filter              <- SpikeTools.kleisliConstructor[IO](100.millis)
          frontendDownsampler <- assembleFrontendDownsampler.mapF(IO.pure(_))
          broadcast           <- Kleisli.liftF(broadcastDataStream(source,
                                                      topics,
                                                      (s: Stream[IO,TaggedSegment]) => s
                                                        .observe(rawSink)
                                                        .through(frontendDownsampler)
                                                        .through(frontendSink),
                                                      hurrSink,
                                                      filter,
          ))
        } yield broadcast

        case Playback(id) => cyborg.io.DB.streamFromDatabaseST(id).flatMapToKleisli { source =>
          for {
            frontendDownsampler <- assembleFrontendDownsampler.mapF(IO.pure(_))
            filter              <- SpikeTools.kleisliConstructor[IO](100.millis)
            broadcast           <- Kleisli.liftF(broadcastDataStream(source,
                                                        topics,
                                                        (s: Stream[IO,TaggedSegment]) => s
                                                          .observe(rawSink)
                                                          .through(frontendDownsampler)
                                                          .through(frontendSink),
                                                        hurrSink,
                                                        filter,
            ))
          } yield broadcast
        }
      }


    /**
      I don't think this should be here, for instance when playback
      */
    // Won't inferr if not broken up. Fun fact: It inferrs correctly with *>
    val startAndBroadcastK = client.startMEAMEserver >> getAndBroadcastDatastream
    startAndBroadcastK(configuration)
  }


  def initProgramState(client: MEAMEHttpClient[IO], dsp: cyborg.dsp.DSP[IO]): IO[ProgramState] = {
    for {
      meameAlive             <- client.pingMEAME
      (dspFlashed, dspAlive) <- dsp.flashDSP
    } yield {
      val setter = meameL.set(MEAMEstate(meameAlive)).andThen(
                   dspL.set(DSPstate(dspFlashed, dspAlive)))

      setter(ProgramState.init)
    }
  }

  def doDspTestStuff(dsp: cyborg.dsp.DSP[IO], conf: FullSettings): IO[Unit] = {
    say("Running DSP test methods!!", Console.RED)
    say("Ensure that the MOCK EXECUTOR is being run!!", Console.RED)
    dsp.setup(conf) >> dsp.setStimgroupPeriod(0, 100.millis) >> dsp.enableStimGroup(0)
  }
}
