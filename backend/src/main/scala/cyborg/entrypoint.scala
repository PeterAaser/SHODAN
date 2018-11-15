package cyborg

import fs2.async.mutable.Signal
import _root_.io.udash.rpc.ClientId
import java.io.IOException
import cats.implicits._

import RPCmessages._
import cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.Topic
import fs2.async._

import cats.effect.IO
import cats.effect._

import wallAvoid.Agent
import utilz._
import utilz.TaggedSegment

import cyborg.io._
import sIO.DB._
import sIO.File._
import sIO.Network._


object staging {


  case class ProgramActions(
    stopAgent       : IO[Unit] = IO.unit,
    stopRecording   : IO[Unit] = IO.unit,
    stopData        : IO[Unit] = IO.unit,
  )

  type Actions = Signal[IO, ProgramActions]
  type State = Signal[IO, ProgramState]

  import cats.kernel.Eq
  implicit val eqPS: Eq[ProgramState] = Eq.fromUniversalEquals

  val placeholder = IO.unit


  def commandPipe(
    topics             : List[Topic[IO,TaggedSegment]],
    rawDataTopic       : Topic[IO,TaggedSegment],
    meameFeedbackSink  : Sink[IO,List[Double]],
    frontendAgentTopic : Topic[IO,Agent],
    state              : Signal[IO,ProgramState],
    getConf            : IO[Setting.FullSettings],
    waveformListeners  : Ref[IO,List[ClientId]],
    agentListeners     : Ref[IO,List[ClientId]])(
    implicit ec        : EC) : IO[Sink[IO,UserCommand]] = {

    def handleMEAMEstateChange(state: State, actions: Actions)(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(_.meameRunning)).changes.tail map { start =>
        say(s"handle meame state change with new state $start")
        if(start) {
          HttpClient.startMEAMEserver(getConf).attempt flatMap{ e =>
            e match {
              case Left(_) => for {
                _         <- state.modify(_.copy( meameRunning = false))
                _         <- IO { say("MEAME not responding") }
              } yield ()

              case Right(_) => for {
                _         <- IO { say("MEAME responding") }
                conf      <- getConf
                tcp       =  streamFromTCP(conf.experimentSettings.segmentLength)
                broadcast <- Assemblers.broadcastDataStream(tcp, topics, rawDataTopic.publish)
                _         <- actions.modify(_.copy(stopData = broadcast.interrupt))
                _         <- state.modify(_.copy(dbRecording = false))
                _         <- broadcast.action
              } yield ()
            }
          }
        }
        else for {
          a         <- actions.get
          _         <- a.stopData
          _         <- actions.modify(_.copy(stopData = IO {say("Running no-op stop data from handle MEAME state change")}))
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }

    def handleRecordingStateChange(state: State, actions: Actions)(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(_.dbRecording)).changes.tail map { start =>
        say(s"handle recording state change with new state $start")
        if(start) for {
          record <- streamToDatabase(rawDataTopic.subscribe(10000), "", getConf)
          _      <- actions.modify(_.copy(stopRecording = record.interrupt))
          _      <- record.action
        } yield ()
        else for {
          a      <- actions.get
          _      <- a.stopRecording
          _      <- actions.modify(_.copy(stopRecording = IO { say("Running no op action stop recording from handle recording state change")} ))
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }


    def handleAgentStateChange(state: State, actions: Actions)(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(_.agentRunning)).changes.tail map { start =>
        say(s"handle agent state change with new state $start")
        if(start) {
          for {
            conf  <- getConf
            agent <- Assemblers.assembleGA(topics, conf.filterSettings.inputChannels, frontendAgentTopic.publish, meameFeedbackSink, getConf)
            _     <- actions.modify(_.copy(stopAgent = agent.interrupt))
            _     <- agent.action
          } yield ()
        }
        else
          for {
            a     <- actions.get
            _     <- a.stopRecording
            _     <- actions.modify(_.copy(stopAgent = IO { say("Running no-op handle stopAgent from handle agent state change") } ))
          } yield ()
      }

      // tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {
      //                                                                      say(z)
      //                                                                      say(z.getStackTrace.toList.mkString("\n"))
      //                                                                      throw z})
      Stream.eval(IO.unit)
    }


    /**
      When playback is requested the broadcaster transmits data from a file.
      */
    def handlePlaybackStateChange(state: State, actions: Actions)(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(z => (z.playbackId, z.playbackRunning))).changes.tail map { case(id, start) =>
        if(start) for {
          _         <- IO.unit
          data      =  streamFromDatabaseThrottled(id)
          broadcast <- Assemblers.broadcastDataStream(data, topics, rawDataTopic.publish)
          _         <- actions.modify(_.copy(stopData = broadcast.interrupt))
          _         <- state.modify(_.copy(dbRecording = false))
          _         <- broadcast.action
        } yield ()
        else for {
          a <- actions.get
          _ <- a.stopData
          _ <- actions.modify(_.copy(stopData = IO { say("Running no-op handle stopData from handle playback state change") }))
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }


    /**
      Create and populate frontend viz
      */
    // TODO: I would like this to not be its own method, but it will do for now.
    def handleFrontendVisualizer(state: State, actions: Actions)(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(z => (z.meameRunning, z.playbackRunning))).changes.tail map { case(a, b) =>
        if(a || b) for {
          _         <- configureAndAttachFrontendWFSink(getConf, waveformListeners, rawDataTopic.subscribe(10))
        } yield ()
        else for {
          _         <- IO.unit
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }


    // TODO: See above
    def handleFrontendAgentVisualizer(state: State, actions: Actions)(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(z => (z.meameRunning, z.agentRunning))).changes.tail map { case(a, b) =>
        if(a || b) for {
          _         <- configureAndAttachFrontendAgentSink(getConf, agentListeners, frontendAgentTopic.subscribe(10))
        } yield ()
        else for {
          _         <- IO.unit
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }



    def doTheThing(stopSignal: Signal[IO,Boolean], action: Signal[IO,ProgramActions]): Pipe[IO,UserCommand,IO[Unit]] = _.map{ c =>
      say(s"got action token guy named $c")
      c match {
        case StartMEAME =>
          state.modify( _.copy( meameRunning = true )).void

        case StopMEAME =>
          state.modify( _.copy( meameRunning = false )).void

        case DBstartRecord =>
          state.modify( _.copy( dbRecording = true )).void

        case DBstopRecord =>
          state.modify( _.copy( dbRecording = false )).void

        case RunFromDB(recording) =>
          state.modify( _.copy( playbackRunning = true,
                                playbackId      = recording.id )).void

        case StopData =>
          state.modify( _.copy( playbackRunning = false,
                                meameRunning    = false )).void

        case AgentStart =>
          state.modify(s => s.copy( agentRunning = true )).void

        case GetSHODANstate(p) => handleGetSHODANstate(p)
        case GetRecordings(p) => handleGetRecordings(p)

        case DspBarf =>
          DspLog.printDspLog

        case Shutdown => {
          say("Johnny number five is not alive")
          throw new IOException("Johnny number 5 is not alive")
        }

        case _ =>
          IO( say(Console.RED + s"UNSUPPORTED ACTION ISSUED\n$c" + Console.RESET) )
      }
    }


    for {
      stopSignal  <- signalOf[IO,Boolean](false)
      actions     <- signalOf[IO,ProgramActions](ProgramActions())
    } yield { _.through(doTheThing(stopSignal, actions))
               .map(Stream.eval).joinUnbounded
               .concurrently(handleMEAMEstateChange(state,actions))
               .concurrently(handleRecordingStateChange(state,actions))
               .concurrently(handleAgentStateChange(state,actions))
               .concurrently(handlePlaybackStateChange(state,actions))
               .concurrently(handleFrontendVisualizer(state,actions))
               .concurrently(handleFrontendAgentVisualizer(state,actions))
    }
  }


  // Configures the frontend sink,
  def configureAndAttachFrontendWFSink(
    getConf              : IO[Setting.FullSettings],
    getWaveformListeners : Ref[IO,List[ClientId]],
    rawStream            : Stream[IO,TaggedSegment])(implicit ec : EC) : IO[Unit] = {

    import cyborg.backend.server.ApplicationServer
    val t = for {
      sink <- Stream.eval(ApplicationServer.waveformSink(getWaveformListeners, getConf))
      _    <- rawStream.through(sink)
    } yield ()

    t.compile.drain
  }


  // Configures the frontend agent sink,
  def configureAndAttachFrontendAgentSink(
    getConf              : IO[Setting.FullSettings],
    getAgentListeners    : Ref[IO,List[ClientId]],
    agentStream          : Stream[IO,Agent])(implicit ec : EC) : IO[Unit] = {

    import cyborg.backend.server.ApplicationServer
    val t = for {
      sink <- Stream.eval(ApplicationServer.agentSink(getAgentListeners, getConf))
      _    <- agentStream.through(sink)
    } yield ()

    t.compile.drain
  }


  // TODO: figure out how to idiomatically handle this stuff
  def handleGetSHODANstate(p: Promise[IO,EquipmentState]): IO[Unit] = {
    def accumulateError(errors: Either[List[EquipmentFailure], Unit])(error: Option[EquipmentFailure]): Either[List[EquipmentFailure], Unit] = {
      error match {
        case Some(err) => errors match {
          case Left(errorList) => Left(err :: errorList)
          case Right(_) => Left(List(err))
        }
        case None => errors
      }
    }

    val errors: Either[List[EquipmentFailure],Unit] = Right(())

    val hurr: IO[Either[List[EquipmentFailure],Unit]] = HttpClient.getMEAMEhealthCheck.map { s =>
      val durr: List[Option[EquipmentFailure]] = List(
        (if(s.isAlive)    None else Some(MEAMEoffline)),
        (if(s.dspAlive)   None else Some(DspDisconnected)),
        (if(!s.dspBroken) None else Some(DspBroken)))

      durr.foldLeft(errors){ case(acc,e) => accumulateError(acc)(e) }
    }
    val huh = hurr.attempt.map {
      case Left(_) => Left(List(MEAMEoffline))
      case Right(e) => e
    }
    huh flatMap(x => p.complete(x))
  }


  def handleGetRecordings(p: Promise[IO,List[RecordingInfo]])(implicit ec: EC): IO[Unit] = for {
    experiments <- getAllExperiments
    _ <- p.complete(experiments)
  } yield ()
}

case class ProgramState(
  meameRunning    : Boolean = false,
  playbackRunning : Boolean = false,
  playbackId      : Int     = 0,
  dbRecording     : Boolean = false,
  agentRunning    : Boolean = false,
  dspAlive        : Boolean = false,
  dspBroken       : Boolean = true
)
