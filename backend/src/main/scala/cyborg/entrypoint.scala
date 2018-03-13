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

import params.GA._
import ControlTokens._

object staging {

  case class ProgramState(
    meameRunning    : Boolean,
    playbackRunning : Boolean,
    playbackId      : Long,
    stopData        : IO[Unit],
    dbRecording     : Boolean,
    stopRecording   : IO[Unit],
    agentRunning    : Boolean,
    stopAgent       : IO[Unit],
  )

  import cats.kernel.Eq
  implicit val eqPS: Eq[ProgramState] = Eq.fromUniversalEquals

  val placeholder = IO.unit

  val init = ProgramState(
    false,
    false,
    0,
    IO.unit,
    false,
    IO.unit,
    false,
    IO.unit,
    )


  def commandPipe(
    topics: List[Topic[IO,TaggedSegment]],
    rawDataTopic: Topic[IO,TaggedSegment],
    meameFeedbackSink: Sink[IO,List[Double]],
    frontendAgentSink: Sink[IO,Agent],
    )(implicit ec: EC): Sink[IO,UserCommand] = {

    def handleMEAMEstateChange(state: Signal[IO,ProgramState])(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(_.meameRunning)).changes.tail map { start =>
        say(s"handle meame state change with new state $start")
        if(start) {
          HttpClient.startMEAMEserver.attempt flatMap{ e =>
            e match {
              case Left(_) => for {
                _         <- state.modify(_.copy( meameRunning = false))
                _         <- IO { say("MEAME not responding") }
              } yield ()

              case Right(_) => for {
                _         <- IO { say("MEAME responding") }
                tcp       =  sIO.streamFromTCP(params.experiment.segmentLength)
                broadcast <- Assemblers.broadcastDataStream(tcp, topics, rawDataTopic.publish)
                _         <- state.modify(_.copy(stopData = broadcast.interrupt))
                _         <- state.modify(_.copy(dbRecording = false))
                _         <- broadcast.action
              } yield ()
            }
          }
        }
        else for {
          s         <- state.get
          _         <- s.stopData
          _         <- state.modify(_.copy(stopData = IO {say("Running no-op stop data from handle MEAME state change")}))
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }

    def handleRecordingStateChange(state: Signal[IO,ProgramState])(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(_.dbRecording)).changes.tail map { start =>
        say(s"handle recording state change with new state $start")
        if(start) for {
          record <- sIO.streamToDatabase(rawDataTopic.subscribe(10000),"")
          _      <- state.modify(_.copy(stopRecording = record.interrupt))
          _      <- record.action
        } yield ()
        else for {
          s      <- state.get
          _      <- s.stopRecording
          _      <- state.modify(_.copy(stopRecording = IO { say("Running no op action stop recording from handle recording state change")} ))
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }

    def handleAgentStateChange(state: Signal[IO,ProgramState])(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(_.agentRunning)).changes.tail map { start =>
        say(s"handle agent state change with new state $start")
        if(start) {
          for {
            agent <- Assemblers.assembleGA(topics, inputChannels, frontendAgentSink, meameFeedbackSink)
            _     <- state.modify(_.copy(stopAgent = agent.interrupt))
            _     <- agent.action
          } yield ()
        }
        else for {
          s <- state.get
          _ <- s.stopRecording
          _ <- state.modify(_.copy(stopAgent = IO { say("Running no-op handle stopAgent from handle agent state change") } ))
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }


    /**
      When playback is requested the broadcaster transmits data from a file.
      */
    def handlePlaybackStateChange(state: Signal[IO,ProgramState])(implicit ec: EC): Stream[IO,Unit] = {
      val tasks = state.discrete.through(_.map(z => (z.playbackId, z.playbackRunning))).changes.tail map { case(id, start) =>
        if(start) for {
          _         <- IO.unit
          data      =  sIO.streamFromDatabase(id.toInt)
          broadcast <- Assemblers.broadcastDataStream(data, topics, rawDataTopic.publish)
          _         <- state.modify(_.copy(stopData = broadcast.interrupt))
          _         <- state.modify(_.copy(dbRecording = false))
          _         <- broadcast.action
        } yield ()
        else for {
          s <- state.get
          _ <- s.stopData
          _ <- state.modify(_.copy(stopData = IO { say("Running no-op handle stopData from handle playback state change") }))
        } yield ()
      }

      tasks.through(_.map(Stream.eval(_))).joinUnbounded.handleErrorWith(z => {say(z); throw z})
    }


    in => {
      Stream.eval(signalOf[IO,Boolean](false)) flatMap { stopSignal =>
        Stream.eval(signalOf[IO,ProgramState](init)) flatMap { state =>
          in.map{ c =>
            state.get flatMap { stateNow =>
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

                case AgentStart if !stateNow.agentRunning =>
                  state.set( stateNow.copy( agentRunning = true ))

                case DspBarf =>
                  DspLog.printDspLog

                case DspStimTest => {
                  say("Firing off stim test")
                  DspCalls.test
                }

                case Shutdown => {
                  say("Johnny number five is not alive")
                  throw new IOException("Johnny number 5 is not alive")
                }

                case _ =>
                  IO( say(Console.RED + s"UNSUPPORTED ACTION ISSUED\n$c" + Console.RESET) )
              }
            }
          }.map(Stream.eval).joinUnbounded
            .concurrently(handleMEAMEstateChange(state))
            .concurrently(handleRecordingStateChange(state))
            .concurrently(handleAgentStateChange(state))
            .concurrently(handlePlaybackStateChange(state))
        }
      }
    }
  }
}
