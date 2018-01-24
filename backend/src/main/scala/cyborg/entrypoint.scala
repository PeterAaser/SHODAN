package cyborg

import scala.concurrent.duration._

import cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.Topic
import fs2.async.mutable.Queue

import cats.effect.IO
import java.io.IOException
import scala.concurrent.ExecutionContext

import wallAvoid.Agent
import utilz._
import utilz.TaggedSegment

object staging {

  import params.GA._
  import HttpCommands._

  def commandPipe(
    topics: List[Topic[IO,TaggedSegment]],
    frontendAgentSink: Sink[IO,Agent],
    meameFeedbackSink: Sink[IO,List[Double]],
    rawDataTopic: Topic[IO,TaggedSegment]
  )(implicit ec: ExecutionContext): Pipe[IO,UserCommand, IO[Unit]] = {

    def go(s: Stream[IO,UserCommand]): Pull[IO, IO[Any], Unit] = {
      s.pull.uncons1 flatMap {
        case Some((cmd, tl)) => {
          val action = cmd match {

            // done
            case StartMEAME => {
              // TODO: Figure out how to idiomatically get rid of uns*feRun here
              // Might finally get a use for the mysterious R parameter from pull
              Stream.eval(HttpClient.startMEAMEserver).run.unsafeRunSync()
              val tcpStream = sIO.streamFromTCP(params.experiment.segmentLength)
              // Assemblers.broadcastDataStream(tcpStream, topics, rawDataTopic.publish).run
              ???
            }

              // done
            case AgentStart =>
              Assemblers.assembleGA(topics, inputChannels, frontendAgentSink, meameFeedbackSink).run

            // done
            case DspConf =>
              HttpClient.dspConfigure

            // done
            case RunFromDB(id) => {
              val dbStream = sIO.streamFromDatabase(1)
              // Assemblers.broadcastDataStream(dbStream, topics, rawDataTopic.publish).run
              ???
            }


            // done
            case DBstartRecord =>
              sIO.streamToDatabase(rawDataTopic.subscribe(10000), "TESTRUN").run
              // sIO.streamToFile(rawDataQueue.dequeueAvailable).run

            case Shutdown =>
              throw new IOException("Johnny number 5 is not alive")

            // done
            case DspStimTest =>
              HttpClient.dspStimTest

            // done
            case DspUploadTest => for {
              _ <- waveformGenerator.sineWave(0, 100.millis, 200.0)
              _ <- waveformGenerator.sineWave(2, 300.millis, 200.0)
              _ <- waveformGenerator.sineWave(4, 600.millis, 200.0)
            } yield ()

            // done
            case DspBarf =>
              HttpClient.dspBarf

            // done
            case DspDebugReset =>
              HttpClient.dspDebugReset

            case _ => {
              println(Console.RED + "UNSUPPORTED ACTION ISSUED" + Console.RESET)
              Stream.empty.covary[IO].run
            }
          }

          Pull.output1(action) >> go(tl)
        }

        case None => Pull.done
      }
    }
    in => go(in).stream.through(_.map(_.map(λ => ())))
  }
}
