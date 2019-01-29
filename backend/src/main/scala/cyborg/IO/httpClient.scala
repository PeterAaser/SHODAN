package cyborg

import cats.data.Kleisli
import cyborg.dsp.calls.DspCalls

import cats.effect.IO
import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.client.blaze._
import org.http4s.Uri
import org.http4s.circe._

import DspRegisters._
import MEAMEmessages._
import Settings._

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._

import scala.concurrent.duration.FiniteDuration
import utilz._

import backendImplicits._


/**
  TODO: The constructor should ideally be a kleisli for the config (test vs MEAME ips)
  */
class MEAMEHttpClient[F[_]](c: Client[F]) {

  def buildUri(path: String): Uri = {
    val ip = params.Network.meameIP
    Uri.fromString(s"http://$ip:8888/$path").toOption.get
  }


  def startMEAMEserver = Kleisli[IO,FullSettings,Unit]{ conf =>
    val params = DAQparams(conf.daq.samplerate, conf.daq.segmentLength)
    for {
      _ <- connectDAQrequest(params)
      _ <- startDAQrequest
    } yield ()
  }


  // TODO slated for removal
  def getMEAMEhealthCheck: IO[MEAMEstatus] = {
    val req = GET(buildUri("status"))
    val gogo = httpClient.expect[MEAMEhealth](req).flatMap { status =>
      if(status.dspAlive)
        IO(MEAMEstatus(true, true, true))
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

    def setRegistersRequest(regs: RegisterSetList): IO[Unit] = {
      val what = regs.asJson
      val req = POST(regs.asJson, buildUri("DSP/write"))
      httpClient.expect[String](req).void
    }


    def readRegistersRequest(regs: RegisterReadList): IO[RegisterReadResponse] = {
      val huh = implicitly[EntityDecoder[IO, RegisterReadResponse]]
      val req = POST(regs.asJson, buildUri("DSP/read"))
      httpClient.expect[RegisterReadResponse](req)
    }


    def dspCall(call: Int, args: (Int,Int)*): IO[Unit] = {
      val funcCall = DspFuncCall(call, args.toList)
      val req = POST(funcCall.asJson, buildUri("DSP/call"))
      httpClient.expect[String](req).void
    }
  }


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // DAQ
  def connectDAQrequest(params: DAQparams): IO[Unit] = {
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
  // TODO: This is janktastic, but idc
  import org.http4s.client._
  import scala.concurrent.ExecutionContext
  import java.util.concurrent._
  val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
  val httpClient: Client[IO] = JavaNetClientBuilder[IO](blockingEC).create
}
