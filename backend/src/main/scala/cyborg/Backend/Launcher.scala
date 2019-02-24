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

import cats._
import cats.effect._
import cats.implicits._

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


    client.use{ c =>
      for {
        topics          <-  List.fill(60)(Topic[IO,TaggedSegment](TaggedSegment(-1, Chunk[Int]()))).sequence
        rawTopic        <-  Topic[IO,TaggedSegment](TaggedSegment(-1, Chunk[Int]()))

        httpClient       =  new MEAMEHttpClient(c)
        dsp              =  new cyborg.dsp.DSP(httpClient)

        initState       <-  initProgramState(httpClient, dsp)
        stateServer     <-  SignallingRef[IO,ProgramState](initState)
        configServer    <-  SignallingRef[IO,FullSettings](FullSettings.default)
        selectedChannel <-  SignallingRef[IO,Int](0)
        commandQueue    <-  Queue.unbounded[IO,UserCommand]
        rpcServer       <-  cyborg.backend.server.ApplicationServer.assembleFrontend(commandQueue, stateServer, configServer, selectedChannel)

        assembler        =  new Assembler(new MEAMEHttpClient(c), rpcServer, rawTopic, topics, commandQueue, selectedChannel, configServer, stateServer, dsp)

        exitCode        <-  assembler.startSHODAN.compile.drain.as(ExitCode.Success)
      } yield exitCode
    }
  }
}
