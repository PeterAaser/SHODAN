package cyborg

import cyborg.wallAvoid.Agent
import fs2._

import fs2.Stream._
import fs2.async.mutable.Queue

import cats.effect.IO
import java.io.IOException
import scala.concurrent.ExecutionContext

import wallAvoid.Agent
import utilz._

object staging {

  type agentFitnessFunction = Agent => Double

  import params.GA._
  import HttpCommands._


  val testInt: Int = 3

  def commandPipe(
    meameTopics: List[DataTopic[IO]],
    frontendAgentSink: Sink[IO,Agent],
    meameFeedbackSink: Sink[IO,List[Double]],
    rawDataQueue: Queue[IO,Int]
  )(implicit ec: ExecutionContext): Pipe[IO,UserCommand, Stream[IO,Unit]] = {

    def go(s: Stream[IO,UserCommand]): Pull[IO, Stream[IO,Unit], Unit] = {
      s.pull.uncons1 flatMap { case Some((cmd, tl)) =>
        {
          println(s"got a $cmd")
          val action = cmd match {


            case StartMEAME =>
              {
                Stream.eval(HttpClient.startMEAMEserver).run.unsafeRunSync()
                sIO.streamFromTCP(meameTopics, rawDataQueue.enqueue)
              }

            case AgentStart =>
                Assemblers.assembleGA(meameTopics, inputChannels, outputChannels, frontendAgentSink, meameFeedbackSink)

            case RunFromDB(id) => // TODO id hardcoded atm
              sIO.streamFromDatabase(1, meameTopics, rawDataQueue.enqueue)

            case StoreToDB(comment) =>
              sIO.streamToDatabase(rawDataQueue.dequeue, comment)

            case Shutdown =>
              throw new IOException("Johnny number 5 is not alive")

            case DspTest =>
              {
                import DspComms._
                Stream.eval(HttpClient.dspTest).run.unsafeRunSync()

                val tests = for {
                  - <- clearDebug()
                  _ <- HttpClient.meameConsoleLog("\n///////////////\n///////////////\nTesting debug reset")
                  - <- clearDebug()
                  - <- getDebug()
                  _ = println("\n------------\n\n")
                  _ <- HttpClient.meameConsoleLog("\n///////////////\n///////////////\nTesting reads")
                  _ <- readTest()
                  - <- getDebug()
                  - <- clearDebug()
                  _ = println("\n------------\n\n")
                  _ <- HttpClient.meameConsoleLog("\n///////////////\n///////////////\nTesting writes")
                  - <- getDebug()
                  _ <- writeTest()
                  - <- getDebug()
                  - <- clearDebug()
                  _ = println("\n------------\n\n")
                  _ <- HttpClient.meameConsoleLog("\n///////////////\n///////////////\nDone :^)")

                } yield ()

                Stream.eval(tests).run.unsafeRunSync()

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
      }
    }
    in: Stream[IO,UserCommand] => go(in).stream
  }
}
