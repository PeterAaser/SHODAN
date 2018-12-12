package cyborg

import cyborg.dsp.calls.DspCalls

import cats.effect.IO
import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.client.blaze._
import org.http4s.Uri
import org.http4s.circe._

import DspRegisters._
import MEAMEmessages._

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._

import scala.concurrent.duration.FiniteDuration
import utilz._

import backendImplicits._

object HttpClient {

  def buildUri(path: String): Uri = {
    val ip = params.http.MEAMEclient.ip
    Uri.fromString(s"http://$ip:8888/$path").toOption.get
  }

  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // General
  def startMEAMEserver(settingServer: IO[Setting.FullSettings]): IO[Unit] = {
    for {
      conf <- settingServer
      params = DAQparams(conf.experimentSettings.samplerate, conf.experimentSettings.segmentLength)
      _ <- connectDAQrequest(params)
      _ <- startDAQrequest
    } yield ()
  }


  def getMEAMEhealthCheck: IO[MEAMEstatus] = {
    // say("pretend MEAME health check inc")
    // say("hello?")
    // say("hello?")
    // IO { MEAMEstatus(true, true, true) }
    val req = GET(buildUri("status"))
    val gogo = httpClient.expect[MEAMEhealth](req).flatMap { status =>
      if(status.dspAlive)
        DspCalls.readElectrodeConfig.map(_ => MEAMEstatus(true, true, true))
      else
        IO(MEAMEstatus(true, false, false))
    }
    gogo.attempt.map {
      case Left(e) => MEAMEstatus(false,false,false)
      case Right(s) => s
    }
  }


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // DSP

  object DSP {
    def flashDsp: IO[Unit] = {
      val req = GET(buildUri("DSP/flash"))
      httpClient.expect[String](req).void
    }

    def setRegistersRequest(regs: RegisterSetList): IO[Unit] =
    {
      val what = regs.asJson
      val req = POST(regs.asJson, buildUri("DSP/write"))
      httpClient.expect[String](req).void
    }


    def readRegistersRequest(regs: RegisterReadList): IO[RegisterReadResponse] =
    {
      val huh = implicitly[EntityDecoder[IO, RegisterReadResponse]]
      val req = POST(regs.asJson, buildUri("DSP/read"))
      httpClient.expect[RegisterReadResponse](req)
    }


    def dspCall(call: Int, args: (Int,Int)*): IO[Unit] =
    {
      val funcCall = DspFuncCall(call, args.toList)
      val req = POST(funcCall.asJson, buildUri("DSP/call"))
      httpClient.expect[String](req).void
    }
  }


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // DAQ
  def connectDAQrequest(params: DAQparams): IO[Unit] =
  {
    val req = POST(params.asJson, buildUri("DAQ/connect"))
    httpClient.expect[String](req).void
  }

  def startDAQrequest: IO[Unit] =
    httpClient.expect[String](GET(buildUri("DAQ/start"))).void

  def stopDAQrequest: IO[Unit] =
    httpClient.expect[String](GET(buildUri("DAQ/stop"))).void



  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // Auxillary
  def meameConsoleLog(s: String): IO[Unit] =
    httpClient.expect[String](POST(s, buildUri("aux/logmsg"))).void


  // #YOLO
  // TODO: This is janktastic.
  import org.http4s.client._
  import scala.concurrent.ExecutionContext
  import java.util.concurrent._
  val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
  val httpClient: Client[IO] = JavaNetClientBuilder[IO](blockingEC).create
}
