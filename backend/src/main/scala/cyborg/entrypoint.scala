package cyborg

import cyborg.wallAvoid.Agent
import com.typesafe.config.ConfigFactory
import fs2._
import MEAMEutilz._


import fs2.Stream._
import fs2.async.mutable.Queue
import fs2.io.tcp._

import cats.effect.IO
import cats.effect.Effect
import cats.effect.Sync
import java.io.IOException
import scala.concurrent.ExecutionContext

import wallAvoid.Agent
import utilz._

import scala.language.higherKinds

object staging {

  type agentFitnessFunction = Agent => Double

  import params.experiment._
  import params.GA._
  import backendImplicits._
  import HttpCommands._


  def commandPipe(
    meameTopics: List[DataTopic[IO]],
    frontendAgentSink: Sink[IO,Agent],
    meameFeedbackSink: Sink[IO,Byte],
    rawDataSink: Sink[IO,Int]
  )(implicit ec: ExecutionContext): Pipe[IO,UserCommand, Stream[IO,Unit]] = {

    def go(s: Stream[IO,UserCommand]): Pull[IO, Stream[IO,Unit], Unit] = {
      s.pull.uncons1 flatMap { case Some((cmd, tl)) =>
        {
          println(s"got a $cmd")
          val action = cmd match {


            case StartMEAME =>
              {
                Stream.eval(HttpClient.startMEAMEserver).run.unsafeRunSync()
                sIO.streamFromTCP(meameTopics, rawDataSink)
              }

            case AgentStart =>
                Assemblers.assembleGA(meameTopics, inputChannels, outputChannels, frontendAgentSink, meameFeedbackSink)

            case RunFromDB(id) => // TODO id hardcoded atm
              sIO.streamFromDatabase(2, meameTopics, rawDataSink)

            case StoreToDB(comment) =>
              sIO.streamToDatabase(meameTopics, comment)

            case Shutdown =>
              throw new IOException("Johnny number 5 is not alive")

            case dspTest =>
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
