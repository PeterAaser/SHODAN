package cyborg

import fs2.async.mutable.Signal
import java.io.IOException
import scala.concurrent.duration._
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
import HttpCommands._

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
      (state.discrete.through(_.map(_.meameRunning)).changes.tail map { start =>
        say(s"handle meame state change with new state $start")
        if(start) for {
          s         <- HttpClient.startMEAMEserver
          tcp       =  sIO.streamFromTCP(params.experiment.segmentLength)
          broadcast <- Assemblers.broadcastDataStream(tcp, topics, rawDataTopic.publish)
          _         <- state.modify(_.copy(stopData = for {
                                             _ <- broadcast.interrupt
                                             _ <- IO { say("Running stop data from handle MEAME state change") }
                                           } yield ()))
          _         <- state.modify(_.copy(dbRecording = false))
          _         <- broadcast.action
        } yield ()
        else for {
          s         <- state.get
          _         <- s.stopData
          _         <- state.modify(_.copy(stopData = IO {say("Running no-op stop data from handle MEAME state change")}))
        } yield ()
      }).through(_.map(Stream.eval(_))).joinUnbounded
    }

    def handleRecordingStateChange(state: Signal[IO,ProgramState])(implicit ec: EC): Stream[IO,Unit] = {
      (state.discrete.through(_.map(_.dbRecording)).changes.tail map { start =>
         say(s"handle recording state change with new state $start")
         if(start) for {
           record <- sIO.streamToDatabase(rawDataTopic.subscribe(10000),"")
           _      <- state.modify(_.copy(stopRecording = for {
                                           _ <- record.interrupt
                                           _ <- IO { say("Running stopRecording from handle recording state change") }
                                         } yield ()))
           _      <- record.action
         } yield ()
         else for {
           s      <- state.get
           _      <- s.stopRecording
           _      <- state.modify(_.copy(stopRecording = IO { say("Running no op action stop recording from handle recording state change")} ))
         } yield ()
       }).through(_.map(Stream.eval(_))).joinUnbounded
    }

    def handleAgentStateChange(state: Signal[IO,ProgramState])(implicit ec: EC): Stream[IO,Unit] = {
      (state.discrete.through(_.map(_.agentRunning)).changes.tail map { start =>
        say(s"handle agent state change with new state $start")
        if(start) for {
          agent <- Assemblers.assembleGA(topics, inputChannels, frontendAgentSink, meameFeedbackSink)
          _     <- state.modify(_.copy(stopAgent = for {
                                         _ <- agent.interrupt
                                         _ <- IO { say("Running stopAgent from handle agent state") }
                                       } yield ()))
          _     <- agent.action
        } yield ()
        else for {
          s <- state.get
          _ <- s.stopRecording
          _ <- state.modify(_.copy(stopAgent = IO { say("Running no-op handle stopAgent from handle agent state change") } ))
        } yield ()
      }).through(_.map(Stream.eval(_))).joinUnbounded
    }


    /**
      When playback is requested the broadcaster transmits data from a file.
      */
    def handlePlaybackStateChange(state: Signal[IO,ProgramState])(implicit ec: EC): Stream[IO,Unit] = {
      (state.discrete.through(_.map(z => (z.playbackId, z.playbackRunning))).changes.tail map { case(id, start) =>
        if(start) for {
          _         <- IO.unit
          data      =  sIO.streamFromDatabase(id.toInt)
          broadcast <- Assemblers.broadcastDataStream(data, topics, rawDataTopic.publish)
          _         <- state.modify(_.copy(stopData = for {
                                             _ <- broadcast.interrupt
                                             _ <- IO { say("Running finalizer for handle playback state change") }
                                           } yield ()))
          _         <- state.modify(_.copy(dbRecording = false))
          _         <- broadcast.action
        } yield ()
        else for {
          s <- state.get
          _ <- s.stopData
          _ <- state.modify(_.copy(stopData = IO { say("Running no-op handle stopData from handle playback state change") }))
        } yield ()
      }).through(_.map(Stream.eval(_))).joinUnbounded
    }


    in => {
      Stream.eval(signalOf[IO,Boolean](false)) flatMap { stopSignal =>
        Stream.eval(signalOf[IO,ProgramState](init)) flatMap { state =>
          in.map{ c =>
            state.get flatMap { stateNow =>
              say(s"got action token guy named $c")
              c match {

                case StartMEAME    => state.modify( _.copy( meameRunning    = true      )).void
                case StopMEAME     => state.modify( _.copy( meameRunning    = false     )).void

                case DBstartRecord => state.modify( _.copy( dbRecording     = true      )).void
                case DBstopRecord  => state.modify( _.copy( dbRecording     = false     )).void

                case RunFromDB(id) => state.modify( _.copy( playbackRunning = true,
                                                            playbackId      = id.toLong )).void

                case StopData      => state.modify( _.copy( playbackRunning = false,
                                                            meameRunning    = false     )).void

                case AgentStart if !stateNow.agentRunning => state.set( stateNow.copy( agentRunning = true ))

                case DspStimTest => HttpClient.dspStimTest.void
                case DspUploadTest => for {
                  _ <- waveformGenerator.sineWave(0, 100.millis, 200.0)
                  _ <- waveformGenerator.sineWave(2, 300.millis, 200.0)
                  _ <- waveformGenerator.sineWave(4, 600.millis, 200.0)
                } yield ()
                case DspBarf => HttpClient.dspBarf.void
                case DspDebugReset => HttpClient.dspDebugReset.void
                case DspConf => HttpClient.dspConfigure.void

                case Shutdown => {
                  say("Johnny number five is not alive")
                  throw new IOException("Johnny number 5 is not alive")
                }

                case _ => IO( say(Console.RED + s"UNSUPPORTED ACTION ISSUED\n$c" + Console.RESET) )
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
