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

import scala.language.higherKinds

object staging {

  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(16, threadName = "fugger")

  type agentFitnessFunction = Agent => Double

  import params.experiment._


  def GArun[F[_]:Async](
    meameReadStream: Stream[F, Int],
    meameWriteSink: Sink[F, Byte],
    frontendAgentObservePipe: Pipe[F,Agent,Agent],
    channels: List[Int]): F[Unit] =
  {

    import params.filtering._

    val inputFilter = Assemblers.assembleInputFilter(channels)
    val spikes = meameReadStream.through(inputFilter)

    val experimentPipe = GApipes.experimentPipe(spikes, layout)

    val toStimFrequencyTransform: List[Double] => String = {
      val logScaler = logScaleBuilder(scala.math.E)
      toStimFrequency(List(3, 6, 9), logScaler)
    }

    val meme = experimentPipe
      .through(frontendAgentObservePipe)
      .through(_.map((λ: Agent) => {λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      //.through(utilz.printPipe(""))
      .through(text.utf8Encode)

    val memeTask = meme.through(meameWriteSink)

    memeTask.run
  }


  def launchGA(
    wsAgentServerPipe: Pipe[Task,Agent,Agent],
    selectChannels: List[Int]
  ): Stream[Task,Unit] =

    networkIO.socketStream[Task](networkIO.selectChannelsPort) flatMap {
      socket => Stream.eval(
        GArun(
          socket.reads(1024*1024).through(utilz.bytesToInts),
          (s: Stream[Task,Byte]) => s.drain,

          wsAgentServerPipe,
          selectChannels
        ))
    }

  def runFromHttp2(
    segmentLength: Int,
    selectChannels: List[Int]
  ): Task[Unit] = {

    import httpCommands._
    val commandQueueTask: Task[Queue[Task,userCommand]] = fs2.async.unboundedQueue[Task,userCommand]
    val wsAgentServerPipe: Pipe[Task,Agent,Agent] = wsIO.webSocketServerAgentObserver
    def printStream(msg: String) = Stream.emit(Task.now(println(msg))).covary[Task]

    def commandPipe: Pipe[Task,userCommand, Task[Unit]] = s => {
      s flatMap { command =>
        printStream("---commandpipe received $command---") ++
        (command match {

          case StartMEAME => {
            httpClient.startMEAMEServer2(samplerate, segmentLength, selectChannels) flatMap { resp =>
              resp match {
                case Left(errorMsg) => printStream("[ERROR]: $errorMsg")
                case Right(_) => printStream("----starting MEAME----")
              }
            }
          }

          case AgentStart => {
            printStream("------Starting Agent------") ++
            Stream.emit(launchGA(wsAgentServerPipe, selectChannels).run)
          }

          case StopMEAME => {
            printStream("NYI, currently all is done in startMEAME...")
          }

          case WfStart => {

            val wfenqueued = networkIO.socketStream[Task](networkIO.allChannelsPort) flatMap { socket =>
              val dataPipe: Pipe[Task,Int,Int] = wsIO.webSocketWaveformObserver

              val enqs = socket.reads(1024*1024)
                .through(utilz.bytesToInts)
                .through(dataPipe)

              enqs.drain
            }

            printStream("------Starting WF------") ++
            Stream.emit(wfenqueued.run)

          }

          case ConfigureMEAME => {
            printStream("NYI, currently all is done in startMEAME...")
          }
          case _ => {
            printStream("You fucked it up dude")
          }
        })
      }
    }

    val doThing = Stream.eval(commandQueueTask) flatMap {
      commandQueue => {
        val httpServerTask: Task[Unit] = httpServer.startServer(commandQueue.enqueue)
        val commands = commandQueue.dequeue.through(commandPipe).map(Stream.eval)

        Stream.eval(httpServerTask) merge concurrent.join(5)(commands)
      }
    }
    doThing.run
  }

}
