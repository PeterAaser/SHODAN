package cyborg

import cats.arrow.FunctionK
import cats.data._
import fs2._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.WallAvoid.Agent
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
import cyborg.VizState._

import cyborg.bonus._

import scala.concurrent.duration._

import backendImplicits._
import RPCmessages._


import org.http4s.client.blaze._
import org.http4s.client._

// import cats.effect.IO.timer

class Assembler(
  httpClient      : MEAMEHttpClient[IO],
  RPCserver       : cyborg.backend.server.RPCserver,
  agentTopic      : Topic[IO,Agent],
  spikes          : Topic[IO,Chunk[Int]],
  topics          : List[Topic[IO,Chunk[Int]]],
  commandQueue    : Queue[IO,UserCommand],
  vizServer       : SignallingRef[IO,VizState],
  configServer    : SignallingRef[IO,FullSettings],
  stateServer     : SignallingRef[IO,ProgramState],
  dsp             : cyborg.dsp.DSP[IO])
  (implicit ioTimer: Timer[IO])
{

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


  /**
    * Maze runner experiment using neural data as input
    */
  def assembleMazeRunner: Kleisli[IO,FullSettings,InterruptableAction[IO]] = Kleisli{ conf =>

    val spikeConst = hardcode(20.millis)
    val spikeTools = new SpikeTools[IO](spikeConst, conf)

    val perturbationTransform = new PerturbationTransforms(conf)

    val perturbationSink: Pipe[IO,Agent,Unit] = agentStream =>
      agentStream
        .observe(agentTopic.publish)
        .flatMap(x => Stream.emits(x.distances.zipIndexLeft))
        .through(perturbationTransform.toStimReqBinary)
        .evalTap(RPCserver.stimReqPush)
        .through(dsp.stimuliRequestSink(conf))

    val spikeStream = wakeUp(topics)

    val mazeRunner = spikeStream.flatMap{ sl =>
      new MazeExperiment[IO](
        conf,
        spikeTools.windowedSpikes(sl.toList).map(_.map(_.toDouble)),
        perturbationSink
      ).run
    }

    val taskStream = for {
      _ <- Stream.eval(dsp.setup(conf))
      _ <- mazeRunner
    } yield ()

    InterruptableAction.apply(taskStream)
  }


  /**
    * Maze runner experiment using a linear toy reservoir
    */
  def assembleMazeRunnerBasicReservoir: Kleisli[IO,FullSettings,InterruptableAction[IO]] = Kleisli{ conf =>
    val reservoir = new SimpleReservoir
    val perturbationSink: Pipe[IO,Agent,Unit] = agentStream =>
    agentStream
      .observe(agentTopic.publish)
      .map(_.distances.toChunk)
      .through(reservoir.perturbationSink)

    val reservoirOutput =
      Stream.eval(IO(reservoir.reservoirState.clone.toChunk)).repeat.metered(2.millis)

    val mazeRunner = {
      new MazeExperiment[IO](
        conf,
        reservoirOutput,
        perturbationSink
      ).run
    }

    InterruptableAction.apply(mazeRunner)
  }
  

  def assembleMazeRunnerRNN: Kleisli[IO,FullSettings,InterruptableAction[IO]] = Kleisli{ conf =>
    import cyborg.dsp.calls.DspCalls._
    import cyborg.DspRegisters._
    import cyborg.MEAMEmessages._

    val buckets = 100
    val inits = List.fill(buckets)(Chunk.seq(List.fill(40)(0.0)))

    val perturbationTransform = new PerturbationTransforms(conf)
    
    val huh = Stream.eval(Ref.of[IO,List[DspFuncCall]](Nil)) flatMap { messages =>
      Stream.eval(Topic[IO,Chunk[Double]](Chunk.empty)) flatMap { topic =>

        val reservoir = new RecurrentReservoir[IO]
        val inputSink: Pipe[IO,DspFuncCall,Unit] = _.evalMap{ msg => messages.update(msgs => msg :: msgs) }

        val perturbationSink: Pipe[IO,Agent,Unit] =
          _.observe(agentTopic.publish)
            .flatMap(x => Stream.emits(x.distances.zipIndexLeft))
            .through(perturbationTransform.toStimReq)
            .through(reservoir.stimuliRequestPipe)
            .through(inputSink)


        val mazeRunner: Stream[IO,Unit] = {
          new MazeExperiment[IO](
            conf,
            topic.subscribe(10)
              .through(stridedSlideWithInit(buckets, buckets - 1)(inits.toChunk))
              .map(_.flatten),
            perturbationSink
          ).run
        }

        mazeRunner.concurrently(Stream.eval(reservoir.start(messages, topic)))
      }
    }

    assert(conf.readout.getLayout.head == conf.readout.buckets*10, "expected ")

    InterruptableAction.apply(huh)
  }


  /**
    Takes a multiplexed dataSource and a list of topics.
    Demultiplexes the data and publishes data to all channel topics.

    MEAME --> BROADCAST
    */
  private def broadcastDataStream(source: Stream[IO,TaggedSegment]): Kleisli[IO,FullSettings,InterruptableAction[IO]] = Kleisli{ conf =>

    def publishSink: Pipe[IO,TaggedSegment, Unit] = {
      val topicsV = topics.toVector
      def go(s: Stream[IO,TaggedSegment]): Pull[IO,Unit,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((taggedSeg, tl)) => {
            val idx = taggedSeg.channel
            if(idx != -1){
              Pull.eval(topicsV(idx).publish1(taggedSeg.data)) >> go(tl)
            }
            else go(tl)
          }
          case None => Pull.done
        }
      }
      in => go(in).stream
    }

    InterruptableAction.apply(source.through(publishSink))
  }


  /**
    BROADCAST --> FRONTEND
    * 
    * All visualization data is done here, this means waveform data, agent, spike visualizations et al.
    */
  def broadcastToFrontend: Kleisli[IO,FullSettings,InterruptableAction[IO]] = Kleisli{ conf =>

    val visualizerStream = vizServer.discrete.changes.switchMap{ vizState =>
      val spikeTools = new SpikeTools[IO](40.millis, conf)
      val feFilter   = new FrontendFilters(conf, vizState, spikeTools)

      val allChannelsStream = feFilter.renderAll(topics)
          .evalMap(data => RPCserver.drawCommandPush(0)(data))

      val select1 = topics(vizState.selectedChannel)
        .subscribe(10)
        .through(feFilter.visualizeRawAvg)
        .evalMap(RPCserver.drawCommandPush(1))

      val select2 = topics(vizState.selectedChannel)
        .subscribe(10)
        .through(feFilter.visualizeNormSpikes)
        .evalMap(RPCserver.drawCommandPush(2))

      val agentStream = agentTopic
        .subscribe(10)
        .evalMap(RPCserver.agentPush)

      val allChannelsSpikes = feFilter.visualizeAllSpikes(topics)
        .evalMap(RPCserver.drawCommandPush(3))

      allChannelsStream
        // .concurrently(Stream.eval(RPCserver.stimReqPush(0, 1.0)))
        // .concurrently(Stream.eval(RPCserver.stimReqPush(1, 2.0)))
        // .concurrently(Stream.eval(RPCserver.stimReqPush(2, 3.0)))
        // .concurrently(select1)
        // .concurrently(select2)
        .concurrently(agentStream)
        .concurrently(allChannelsSpikes)
    }


    InterruptableAction.apply(visualizerStream)
  }


  /**
    The raw sink stuff is a little bolted on due to some design mistakes.
    The problem was a wish to separate recording and data, but I lacked a reference 
    to the stream in the ctrl pipe
    
    CONFIG --> (MEAME --> BROADCASTER)
    */
  def startBroadcast(programState: ProgramState): Kleisli[IO,FullSettings,InterruptableAction[IO]] = {

      /**
        This must be run first since the kleisli for the DB case is actually a StateT in disguise!
        In english: The BD call alters the configuration, thus to ensure synchronized configs it
        must be used first so that downstream configs match the playback.

        Would be nice to change this I suppose, but it is what it is
        
        TODO: I don't think this works!!
        */
      import backendImplicits._

      val getAndBroadcastDatastream = programState.dataSource.get match {
        case Live => for {
          _         <- this.httpClient.startMEAMEserver
          source    <- cyborg.io.Network.streamFromTCP.mapF(IO.pure(_))
          broadcast <- broadcastDataStream(source)
        } yield broadcast

          // TODO: This does not work the way I want it to. This needs to be rethought!
          // The issue is that the config is changed mid-conf after picking up a recording from database.
          // One possible solution is to split into two configs, the user config which stays immutable, and a state
          // config that is being built (which at the moment only entails picking up settings from the database).
        case Playback(id) => cyborg.io.DB.streamFromDatabaseST(id).flatMapToKleisli { (source: Stream[IO,TaggedSegment]) =>
          for {
            broadcast <- broadcastDataStream(source)
          } yield broadcast
        }
      }

      getAndBroadcastDatastream
    }


  /**
    * Checks MEAME aliveness and flashes DSP.
    * Consider adding a call for checking DSP aliveness before flashing as flashing tends to kill the DSP and
    * wastes time.
    */
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


  /**
    * Starts a recording to the database.
    * This action will upon receiving data create a file and a database entry (but not before)
    */
  def recordToFile = Kleisli[IO,FullSettings,InterruptableAction[IO]]{ conf =>
    val dataStream: Stream[IO, Int] = wakeUp(topics).flatMap{ (sl: Chunk[Stream[IO,Chunk[Int]]]) => roundRobinC(sl.toList) }.hideChunks.hideChunks
    io.database.databaseIO.streamToDatabase(dataStream, "Just a test")(conf)
  }
}
