package cyborg.backend


import fs2._
import fs2.concurrent.Topic
import fs2.concurrent.SignallingRef
import fs2.concurrent.Queue

import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.Client
import scala.concurrent.duration._

import cyborg._
import scala.util.Random
import utilz._

import cyborg.State._
import cyborg.Settings._
import cyborg.RPCmessages._
import cyborg.VizState._

import cats._
import cats.effect._
import cats.implicits._

import cyborg.wallAvoid.Agent

import scala.concurrent.ExecutionContext

object Launcher extends IOApp {

  val client = BlazeClientBuilder[IO](ExecutionContext.global).resource

  def run(args: List[String]): IO[ExitCode] = {                                                                                                                                                                                                                                                                                                               say("wello")


    if(params.Network.mock){
      say("Starting test server!")
      cyborg.mockServer.unsafeStartTestServer
    }


    def initProgramState(client: MEAMEHttpClient[IO], dsp: cyborg.dsp.DSP[IO]): IO[ProgramState] = {
      for {
        meameAlive             <- client.pingMEAME
        (dspFlashed, dspAlive) <- dsp.flashDSP
      } yield {
        val setter = meameL.set(MEAMEstate(meameAlive)).andThen(
          dspL.set(DSPstate(dspFlashed, dspAlive)))

        setter(ProgramState.init)
      }
    }


    val gogo = client.use{ c =>

      for {
        topics          <-  List.fill(60)(Topic[IO,Chunk[Int]](Chunk.empty[Int])).sequence
        agent           <-  Topic[IO,Agent](Agent.init)
        spikes          <-  Topic[IO,Chunk[Int]](Chunk.empty)

        httpClient       =  new MEAMEHttpClient(c)
        dsp              =  new cyborg.dsp.DSP(httpClient)
        initState       <-  initProgramState(httpClient, dsp)

        stateServer     <-  SignallingRef[IO,ProgramState](initState)
        configServer    <-  SignallingRef[IO,FullSettings](FullSettings.default)
        vizServer       <-  SignallingRef[IO,VizState](VizState.default)

        commandQueue    <-  Queue.unbounded[IO,UserCommand]
        rpcServer       <-  cyborg.backend.server.ApplicationServer.assembleFrontend(
          commandQueue,
          stateServer,
          configServer,
          vizServer
        )

        assembler        =  new Assembler(
          new MEAMEHttpClient(c),
          rpcServer,
          agent,
          spikes,
          topics,
          commandQueue,
          vizServer,
          configServer,
          stateServer,
          dsp
        )

        exitCode        <-  assembler.startSHODAN.compile.drain.as(ExitCode.Success)
      } yield exitCode
    }
    gogo

    // import wallAvoid._
    // val initAgent = Agent.init.copy(loc = Coord(2000.0, 5000.0), heading = 0.3)
    // val agentStream = Stream.iterate(initAgent)(_.autopilot).take(40)
    // val zippedWithPrev = agentStream.zipWithPrevious.fold(0.0){
    //   case (acc, (prevAgent, nextAgent)) => prevAgent.map{ prev =>
    //     val autopilot = prev.autopilot
    //     val diff = Math.abs(nextAgent.heading - autopilot.heading)
    //     say(s"previous agent was ${prev}")
    //     say(s"this agent was     ${nextAgent}")
    //     say(s"autopilot agent:   ${autopilot}")
    //     say(s"diff: " + "%.4f".format(diff) + "\n\n\n")
    //     acc + diff
    //   }.getOrElse(acc)
    // }.toList.foreach(say(_))

    // IO.unit.as(ExitCode.Success)
  }
}
