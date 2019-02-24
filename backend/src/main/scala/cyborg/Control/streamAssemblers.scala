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


class Assembler(httpClient   : MEAMEHttpClient[IO],
                RPCserver    : cyborg.backend.server.RPCserver,
                rawTopic     : Topic[IO,TaggedSegment],
                topics       : List[Topic[IO,TaggedSegment]],
                commandQueue : Queue[IO,UserCommand],
                drawChannel  : SignallingRef[IO,Int],
                configServer : SignallingRef[IO,FullSettings],
                stateServer  : SignallingRef[IO,ProgramState],
                dsp          : cyborg.dsp.DSP[IO]) {

  def startSHODAN: Stream[IO, Unit] = {

    for {
      _            <- Stream.eval(RPCserver.start)
      commandPipe  <- Stream.eval(ControlPipe.controlPipe(this,
                                                          stateServer,
                                                          configServer,
                                                          commandQueue))

      _            <- Ssay[IO]("###### All systems go ######", Console.GREEN_B + Console.WHITE)
      _            <- Ssay[IO]("###### All systems go ######", Console.GREEN_B + Console.WHITE)
      _            <- Ssay[IO]("###### All systems go ######", Console.GREEN_B + Console.WHITE)

      _            <- commandQueue.dequeue.through(commandPipe)
    } yield ()
  }


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
    */
  def broadcastDataStream(source : Stream[IO,TaggedSegment]): Kleisli[IO,FullSettings,InterruptableAction[IO]] = Kleisli{ conf =>

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

    val filter = new SpikeTools[IO](100.millis, conf)
    def drawStream = drawChannel.discrete.changes.switchMap(channel =>
      filter.visualize(topics(channel).subscribe(10).map(_.data).chunkify).evalMap(RPCserver.drawCommandPush(0)))

    def drawStream2 = drawChannel.discrete.changes.switchMap(channel =>
      topics(channel).subscribe(10).map(_.data).chunkify.through(filter.visualize2).evalMap(RPCserver.drawCommandPush(1))
    )


    InterruptableAction.apply(
        source
          .observe(rawTopic.publish)
          .through(publishSink(topics))
          .concurrently(drawStream)
          .concurrently(drawStream2)
    )
  }


  /**
    Downsamples a stream into two points per second per pixel
    */
  def assembleFrontendDownsampler = Kleisli[Id,FullSettings,Pipe[IO,TaggedSegment,Array[Int]]]{ conf =>

    // huh??
    // what about msgs per sec?
    val targetSegmentLength = (conf.daq.samplerate/conf.daq.segmentLength)

    in => in
      .map(_.downsampleHiLo(conf.daq.segmentLength/targetSegmentLength))
      .chunkify
      .through(mapN(targetSegmentLength*60*2, _.toArray)) // multiply by 2 since we're dealing with max/min
  }


  def broadcastToFrontend: Kleisli[IO,FullSettings,InterruptableAction[IO]] = {

    val interrupted = SignallingRef[IO,Boolean](false)
    Kleisli[IO,FullSettings,InterruptableAction[IO]](assembleFrontendDownsampler.map{ filter =>
      interrupted.map{ interruptSignal =>
        InterruptableAction(
          interruptSignal.set(true),
          this.rawTopic.subscribe(10000).through(filter).evalMap(RPCserver.waveformPush).compile.drain
        )
      }
    }.run)
  }


  /**
    The raw sink stuff is a little bolted on due to some design mistakes.
    The problem was a wish to separate recording and data, but I lacked a reference 
    to the stream in the ctrl pipe
    */
  def startBroadcast(
    configuration : FullSettings,
    programState  : ProgramState
  ): IO[InterruptableAction[IO]] = {

    say("starting broadcast")

    /**
      This must be run first since the kleisli for the DB case is actually a StateT in disguise!
      In english: The BD call alters the configuration, thus to ensure synchronized configs it
      must be used first so that downstream configs match the playback.

      Would be nice to change this I suppose, but it is what it is
      */
    import backendImplicits._

    val getAndBroadcastDatastream = programState.dataSource.get match {
      case Live => for {
        _                   <- this.httpClient.startMEAMEserver
        source              <- cyborg.io.Network.streamFromTCP.mapF(IO.pure(_))
        frontendDownsampler <- assembleFrontendDownsampler.mapF(IO.pure(_))
        broadcast           <- broadcastDataStream(source)
      } yield broadcast

      case Playback(id) => cyborg.io.DB.streamFromDatabaseST(id).flatMapToKleisli { source =>
        for {
          frontendDownsampler <- assembleFrontendDownsampler.mapF(IO.pure(_))
          broadcast           <- broadcastDataStream(source)
        } yield broadcast
      }
    }

    getAndBroadcastDatastream(configuration)
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
    // say("Running DSP test methods!!", Console.RED)
    // say("Ensure that the MOCK EXECUTOR is being run!!", Console.RED)
    // dsp.setup(conf) >>
    //   dsp.setStimgroupPeriod(0, 100.millis) >> 
    //   dsp.enableStimGroup(0)
      // IO{ Thread.sleep(1000) } >>
      // dsp.dspCalls.readStimQueueState >>
      // dsp.dspCalls.checkForErrors.map(x => say(x))
    IO.unit
  }
}
