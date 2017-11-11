package cyborg

import cyborg.wallAvoid.Agent
import fs2._
import fs2.async.mutable.{ Queue, Topic }
import fs2.async.mutable.Queue

import cats.effect.IO
import java.io.IOException
import scala.concurrent.ExecutionContext

import wallAvoid.Agent
import utilz._
import utilz.TaggedSegment

object staging {

  type agentFitnessFunction = Agent => Double

  import params.GA._
  import HttpCommands._

  def commandPipe(
    topics: List[Topic[IO,TaggedSegment]],
    frontendAgentSink: Sink[IO,Agent],
    meameFeedbackSink: Sink[IO,List[Double]],
    rawDataQueue: Queue[IO,TaggedSegment]
  )(implicit ec: ExecutionContext): Pipe[IO,UserCommand, Stream[IO,Unit]] = {

    def go(s: Stream[IO,UserCommand]): Pull[IO, Stream[IO,Unit], Unit] = {
      s.pull.uncons1 flatMap { case Some((cmd, tl)) =>
        {
          println(s"got a $cmd")
          val action = cmd match {


            case StartMEAME =>
              {
                Stream.eval(HttpClient.startMEAMEserver).run.unsafeRunSync()
                val tcpStream = sIO.streamFromTCP(params.experiment.segmentLength)
                Assemblers.broadcastDataStream(tcpStream, topics, rawDataQueue.enqueue)
              }

            case AgentStart =>
                Assemblers.assembleGA(topics, inputChannels, outputChannels, frontendAgentSink, meameFeedbackSink)

            // TODO id hardcoded atm
            case RunFromDB(id) =>
              {
                val dbStream = sIO.streamFromDatabase(1)
                Assemblers.broadcastDataStream(dbStream, topics, rawDataQueue.enqueue)
              }

            case StoreToDB(comment) =>
              sIO.streamToDatabase(rawDataQueue.dequeue, comment)

            case Shutdown =>
              throw new IOException("Johnny number 5 is not alive")

            case DspTest =>
              {
                println(Console.RED + "UNSUPPORTED ACTION ISSUED" + Console.RESET)
                val uhm: Stream[IO,Unit] = Stream.empty
                uhm
              }

            case _ =>
              {
                println(Console.RED + "UNSUPPORTED ACTION ISSUED" + Console.RESET)
                val uhm: Stream[IO,Unit] = Stream.empty
                uhm
              }
          }

          Pull.output1(action) >> go(tl)
        }
        case None => {
          println("commandpipe none pull")
          Pull.done
        }
      }
    }
    in: Stream[IO,UserCommand] => go(in).stream
  }
}
