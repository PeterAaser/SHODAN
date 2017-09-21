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
import scala.concurrent.ExecutionContext

import wallAvoid.Agent
import utilz._

import scala.language.higherKinds

object staging {

  type agentFitnessFunction = Agent => Double

  import params.experiment._
  import params.GA._
  import backendImplicits._
  import httpCommands._


  def commandPipe(
    dbTopics: List[dataTopic[IO]],
    meameTopics: List[dataTopic[IO]],
    frontendAgentSink: Sink[IO,Agent],
    meameFeedbackSink: Sink[IO,Byte]
  )(implicit ec: ExecutionContext): Pipe[IO,userCommand, Stream[IO,Unit]] = {

    def go(s: Stream[IO,userCommand]): Pull[IO, Stream[IO,Unit], Unit] = {
      s.pull.uncons1 flatMap { case Some((cmd, tl)) =>
        {
          println(s"got a $cmd")
          val action = cmd match {


            case StartMEAME =>
              {
                val huh = httpClient.startMEAMEServer.runLog.unsafeRunSync().head
                huh match {
                  case Left(e) => println("error!")
                  case Right(_) => println("MEAME started")
                }
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
                // TODO: identity pipe is missing, but this seems to just be a placeholder anyways
                // Assemblers.assembleWebsocketVisualizer[IO](meameTopics, pipe.id)
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
    in: Stream[IO,userCommand] => go(in).stream
  }
}
