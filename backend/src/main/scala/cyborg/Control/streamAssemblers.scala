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
  topics       : List[Topic[IO,TaggedSegment]],
  commandQueue : Queue[IO,UserCommand],
  zoomLevel    : SignallingRef[IO,Int],
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

    MEAME --> BROADCAST
    */
  private def broadcastDataStream(source: Stream[IO,TaggedSegment]): Kleisli[IO,FullSettings,InterruptableAction[IO]] = Kleisli{ conf =>

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

    InterruptableAction.apply(source.through(publishSink(topics)))
  }


  /**
    BROADCAST --> FRONTEND
    */
  def broadcastToFrontend: Kleisli[IO,FullSettings,InterruptableAction[IO]] = Kleisli{ conf =>
    def renderer(f: Int => Int): Pipe[IO,TaggedSegment,DrawCommand] = FrontendFilters.assembleFrontendRenderer(conf)(f)
    val pixelsPerPull = 10

    /**
      Pretty crazy thingy, might break lol.
      */
    def gogo(zl: Int): Pull[IO,Array[DrawCommand],Unit] = {

      var buf = Array.ofDim[DrawCommand](pixelsPerPull*60)
      val streams: Array[Stream[IO,DrawCommand]] = topics.toArray.map(_.subscribe(10).through(renderer(FrontendFilters.zoomFilter(zl))))

      def go(idx: Int): Pull[IO,Array[DrawCommand],Unit] = {
        streams(idx).pull.unconsN(pixelsPerPull, false).flatMap{
          case Some((chunk, tl)) => {
            chunk.copyToArray(buf, idx*pixelsPerPull)
            streams(idx) = tl
            if(idx == 59){
              for {
                _ <- Pull.output1(buf)
                _ <- Pull.eval(IO{buf = Array.ofDim[DrawCommand](pixelsPerPull*60)})
                _ <- go(0)
              } yield ()
            }
            else
              go(idx+1)
          }
        }
      }
      go(0)
    }


    InterruptableAction.apply(
      zoomLevel.discrete.changes.switchMap(
        zoomLevel => gogo(zoomLevel)
          .stream.map(Array(_))
          .evalMap(data => RPCserver.drawCommandPush(0)(data))))
  }


  /**
    The raw sink stuff is a little bolted on due to some design mistakes.
    The problem was a wish to separate recording and data, but I lacked a reference 
    to the stream in the ctrl pipe
    
    CONFIG --> (MEAME --> BROADCASTER)
    */
  def startBroadcast(
    configuration : FullSettings,
    programState  : ProgramState
  ): IO[InterruptableAction[IO]] = {

    // say("starting broadcast")

    /**
      This must be run first since the kleisli for the DB case is actually a StateT in disguise!
      In english: The BD call alters the configuration, thus to ensure synchronized configs it
      must be used first so that downstream configs match the playback.

      Would be nice to change this I suppose, but it is what it is
      */
    import backendImplicits._

    val getAndBroadcastDatastream = programState.dataSource.get match {
      case Live => for {
        _         <- this.httpClient.startMEAMEserver
        source    <- cyborg.io.Network.streamFromTCP.mapF(IO.pure(_))
        broadcast <- broadcastDataStream(source)
      } yield broadcast

      case Playback(id) => cyborg.io.DB.streamFromDatabaseST(id).flatMapToKleisli { source =>
        for {
          broadcast <- broadcastDataStream(source)
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
