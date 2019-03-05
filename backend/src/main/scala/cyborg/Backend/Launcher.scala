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

    // if(params.Network.mock){
    //   say("Starting test server!")
    //   cyborg.mockServer.unsafeStartTestServer
    // }

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
  }
}
