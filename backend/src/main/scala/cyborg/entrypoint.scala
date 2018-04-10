package cyborg

import fs2.async.mutable.Signal
import java.io.IOException
import cats.implicits._

import cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.Topic
import fs2.async._

import cats.effect.IO
import cats.effect._

import wallAvoid.Agent
import utilz._
import utilz.TaggedSegment


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
    topics:            List[Topic[IO,TaggedSegment]],
    rawDataTopic:      Topic[IO,TaggedSegment],
    meameFeedbackSink: Sink[IO,List[Double]],
    frontendAgentSink: Sink[IO,Agent],
    state:             Signal[IO,ProgramState],
    getConf:           IO[Setting.FullSettings])(
    implicit ec:       EC
  ): IO[Sink[IO,UserCommand]] = {

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
                tcp       =  sIO.streamFromTCP(conf.experimentSettings.segmentLength)
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
          record <- sIO.streamToDatabase(rawDataTopic.subscribe(10000), "", getConf)
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
            agent <- Assemblers.assembleGA(topics, conf.filterSettings.inputChannels, frontendAgentSink, meameFeedbackSink, getConf)
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

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }


    /**
      When playback is requested the broadcaster transmits data from a file.
      */
    def handlePlaybackStateChange(state: State, actions: Actions)(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(z => (z.playbackId, z.playbackRunning))).changes.tail map { case(id, start) =>
        if(start) for {
          _         <- IO.unit
          data      =  sIO.streamFromDatabase(id.toInt)
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

        case RunFromDB(id) =>
          state.modify( _.copy( playbackRunning = true,
                                playbackId      = id.toLong )).void

        case RunFromDBNewest =>
          for {
            newest <- databaseIO.newestRecordingId
            _      <- state.modify( _.copy( playbackRunning = true,
                                            playbackId      = newest)).void
          } yield ()

        case StopData =>
          state.modify( _.copy( playbackRunning = false,
                                meameRunning    = false )).void

        case AgentStart =>
          state.modify(s => s.copy( agentRunning = true )).void

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
      meameHealth <- HttpClient.getMEAMEhealthCheck
    } yield { _.through(doTheThing(stopSignal, actions))
               .map(Stream.eval).joinUnbounded
               .concurrently(handleMEAMEstateChange(state,actions))
               .concurrently(handleRecordingStateChange(state,actions))
               .concurrently(handleAgentStateChange(state,actions))
               .concurrently(handlePlaybackStateChange(state,actions))
    }
  }
}

case class ProgramState(
  meameRunning    : Boolean = false,
  playbackRunning : Boolean = false,
  playbackId      : Long    = 0,
  dbRecording     : Boolean = false,
  agentRunning    : Boolean = false,
  dspAlive        : Boolean = false,
  dspBroken       : Boolean = true
)
