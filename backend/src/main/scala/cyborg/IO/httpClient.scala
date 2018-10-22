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

import _root_.io.circe.literal._
import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import _root_.io.circe.{ Encoder, Json }


import DspRegisters._
import utilz._


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
    say("MEAME health check inc")
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
      val req = POST(buildUri("DSP/write"), regs.asJson)
      httpClient.expect[String](req).void
    }


    def readRegistersRequest(regs: RegisterReadList): IO[RegisterReadResponse] =
    {
      val req = POST(buildUri("DSP/read"), regs.asJson)
      httpClient.expect[RegisterReadResponse](req)
    }


    def dspCall(call: Int, args: (Int,Int)*): IO[Unit] =
    {
      val funcCall = DspFuncCall(call, args.toList)
      val req = POST(buildUri("DSP/call"), funcCall.asJson)
      httpClient.expect[String](req).void
    }
  }


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // DAQ
  def connectDAQrequest(params: DAQparams): IO[Unit] =
  {
    val req = POST(buildUri("DAQ/connect"), params.asJson)
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
    httpClient.expect[String](POST(buildUri("aux/logmsg"), s)).void





  case class DAQparams(samplerate: Int, segmentLength: Int)
  case class DspFuncCall(func: Int, args: List[(Int, Int)])
  object DspFuncCall {
    def apply(func: Int, args: (Int,Int)*): DspFuncCall = {
      DspFuncCall(func, args.toList)
    }}

  case class MEAMEhealth(isAlive: Boolean, dspAlive: Boolean)
  case class MEAMEstatus(isAlive: Boolean, dspAlive: Boolean, dspBroken: Boolean)

  // don't have to read the docs if you just make an ugly hack
  case class DspFCS(func: Int, argAddrs: List[Int], argVals: List[Int])
  implicit val DspFCSCodec = jsonOf[IO, DspFCS]
  implicit val encodeFoo: Encoder[DspFuncCall] = new Encoder[DspFuncCall] {
    final def apply(a: DspFuncCall): Json = {
      val (words, addrs) = a.args.unzip
      DspFCS(a.func, addrs, words).asJson
    }
  }

  implicit val regSetCodec = jsonOf[IO, RegisterSetList]
  implicit val DAQdecoder = jsonOf[IO, DAQparams]
  implicit val regReadCodec = jsonOf[IO, RegisterReadList]
  implicit val regReadRespCodec = jsonOf[IO, RegisterReadResponse]
  implicit val dspCallCodec = jsonOf[IO, DspFuncCall]
  implicit val MEAMEhealthCodec = jsonOf[IO, MEAMEhealth]
  implicit val MEAMEstatusCodec = jsonOf[IO, MEAMEstatus]

  val httpClient = PooledHttp1Client[IO]()
}
