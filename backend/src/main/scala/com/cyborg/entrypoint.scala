package com.cyborg

import com.cyborg.wallAvoid.Agent
import com.typesafe.config.ConfigFactory
import fs2._
import fs2.util.Async
import MEAMEutilz._


import fs2.Stream._
import fs2.async.mutable.Queue
import fs2.util.Async
import fs2.io.tcp._

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
    dbTopics: List[dataTopic[Task]],
    meameTopics: List[dataTopic[Task]],
    frontendAgentSink: Sink[Task,Agent],
    meameFeedbackSink: Sink[Task,Byte]
  ): Pipe[Task,userCommand, Stream[Task,Unit]] = {

    def go: Handle[Task, userCommand] => Pull[Task, Stream[Task,Unit], Unit] = h => {
      h.await1 flatMap { case (cmd, h) =>
        println(s"got a $cmd")
        val action = cmd match {


          case StartMEAME =>
            {
              val huh = httpClient.startMEAMEServer.runLog.unsafeRun().head
              huh match {
                case Left(e) => println("error!")
                case Right(_) => println("MEAME started")
              }
              sIO.streamFromTCP(meameTopics)
            }


          case AgentStart =>
            {
              Assemblers.assembleGA(
                meameTopics,
                inputChannels,
                outputChannels,
                frontendAgentSink,
                meameFeedbackSink)
            }


          case RunFromDB(id) =>
            {
              sIO.streamFromDatabase(id, meameTopics)
            }


          case StoreToDB(comment) =>
            {
              sIO.streamToDatabase(meameTopics, comment)
            }


          case StartWaveformVisualizer =>
            {
              println("Starting wf assembler")
              Assemblers.assembleWebsocketVisualizer[Task](meameTopics, pipe.id)
            }

          case wat: Any =>
            {
              println(wat)
              println("Unsupported")
              Stream.empty
            }
        }

        Pull.output1(action) >> go(h)
      }
    }
    _.pull(go)
  }
}
