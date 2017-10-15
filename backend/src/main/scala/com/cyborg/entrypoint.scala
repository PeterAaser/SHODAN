package com.cyborg

import com.cyborg.wallAvoid.Agent
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
    meameFeedbackSink: Sink[IO,Byte]
  )(implicit ec: ExecutionContext): Pipe[IO,UserCommand, Stream[IO,Unit]] = {

    def go(s: Stream[IO,UserCommand]): Pull[IO, Stream[IO,Unit], Unit] = {
      s.pull.uncons1 flatMap { case Some((cmd, tl)) =>
        {
          println(s"got a $cmd")
          val action = cmd match {


            case StartMEAME =>
              {
                println("Streaming the dataz from MEAME")
                val huh = Stream.eval(HttpClient.startMEAMEserver)
                huh.run.unsafeRunSync()
                sIO.streamFromTCP(meameTopics)
              }


            case AgentStart =>
              {
                val uhm: Stream[IO,Unit] = Assemblers.assembleGA(
                  meameTopics,
                  inputChannels,
                  outputChannels,
                  frontendAgentSink,
                  meameFeedbackSink)
                uhm
              }


            case RunFromDB(id) =>
              {
                println("running from DB!!")
                val uhm = sIO.streamFromDatabase(id, meameTopics)
                uhm
              }


            case StoreToDB(comment) =>
              {
                val uhm = sIO.streamToDatabase(meameTopics, comment)
                uhm
              }


            case StartWaveformVisualizer =>
              {
                println("Starting wf assembler")
                println(Console.RED + "NOT IMPLEMENTED" + Console.RESET)
                // Assemblers.assembleWebsocketVisualizer(meameTopics, utilz.downSamplePipe(100))
                val uhm: Stream[IO,Unit] = Stream.empty
                uhm
                // uhm
              }


            case Shutdown =>
              {
                println("shutting down")
                val ex = new IOException("Johnny number 5 is not alive")
                throw ex
              }

            case dspTest =>
              {

                val huh = Stream.eval(HttpClient.dspTest)
                huh.run.unsafeRunSync()
                val uhm: Stream[IO,Unit] = Stream.empty
                uhm
              }

            case _ =>
              {
                println("Unsupported")
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
