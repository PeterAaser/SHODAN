package cyborg

import cats.MonadError
import cats.data.Kleisli

import cats.effect._
import cats.implicits._
import java.io.FileWriter

import org.http4s._
import org.http4s.client.Client
import org.http4s.client.blaze._
import org.http4s.implicits._
import org.http4s.Uri
import org.http4s.circe._

import cyborg.dsp.calls.DspCalls
import DspRegisters._
import MEAMEmessages._
import Settings._

import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import _root_.io.circe.Decoder
import _root_.io.circe.Encoder

import scala.concurrent.duration.FiniteDuration
import utilz._

import backendImplicits._


/**
  TODO: The constructor should ideally be a kleisli for the config (test vs MEAME ips)
  */
class MEAMEHttpClient[F[_]: Sync](httpClient: Client[F])(implicit ev: MonadError[F,Throwable]){

  say(s"Using ${params.Network.meameIP}")

  implicit def jsonDecoder[A <: Product: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]
  implicit def jsonEncoder[A <: Product: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]

  val clientDSL = new org.http4s.client.dsl.Http4sClientDsl[F]{}
  val http4sDSL = org.http4s.dsl.Http4sDsl[F]
  import clientDSL._
  import http4sDSL._

  def buildUri(path: String): Uri = {
    val ip = params.Network.meameIP
    Uri.fromString(s"http://$ip:8888/$path").toOption.get
  }


  import scala.concurrent.duration._
  import scala.concurrent.CancellationException
  def pingMEAME(implicit ev: MonadError[F,Throwable], timer: Timer[F], cs: ContextShift[F]): F[Boolean] = {
    val req = GET(buildUri("status"))
    httpClient.expect[String](req)
      .map(_ => true)
      .handleError{e => say(s"Warning: MEAME is unreachable or not responding\n$e", Console.RED); false}
  }


  def startMEAMEserver = Kleisli[F,FullSettings,Unit]{ conf =>
    val params = DAQparams(conf.daq.samplerate, conf.daq.segmentLength)
    for {
      _ <- connectDAQrequest(params)
      _ <- startDAQrequest
    } yield ()
  }


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // DSP
  object DSP {
    def flashDsp: F[Unit] = {
      val req = GET(buildUri("DSP/flash"))
      httpClient.expect[String](req).void
    }

    def setRegistersRequest(regs: RegisterSetList): F[Unit] = {
      val what = regs.asJson
      val req = POST(regs.asJson, buildUri("DSP/write"))

      val fw = new FileWriter("/home/peteraa/SHODANlog.txt", true) ;
      fw.write(s"${regs.asJson.toString()},\n" ) ;
      fw.close()

      httpClient.expect[String](req).void
    }


    def readRegistersRequest(regs: RegisterReadList): F[RegisterReadResponse] = {
      val huh = implicitly[EntityDecoder[F, RegisterReadResponse]]
      val req = POST(regs.asJson, buildUri("DSP/read"))
      httpClient.expect[RegisterReadResponse](req)
    }


    def dspCall(call: Int, args: (Int,Int)*): F[Unit] = {
      val funcCall = DspFuncCall(call, args.toList)
      val req = POST(funcCall.asJson, buildUri("DSP/call"))

      val fw = new FileWriter("/home/peteraa/SHODANlog.txt", true) ;
      fw.write(s"${funcCall.asJson.toString()},\n" ) ;
      fw.close()

      httpClient.expect[String](req).void
    }
  }


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // DAQ
  def connectDAQrequest(params: DAQparams): F[Unit] = {
    val req = POST(params.asJson, buildUri("DAQ/connect"))
    httpClient.expect[String](req).void
  }

  def startDAQrequest: F[Unit] =
    httpClient.expect[String](GET(buildUri("DAQ/start"))).void

  def stopDAQrequest: F[Unit] =
    httpClient.expect[String](GET(buildUri("DAQ/stop"))).void



  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // Auxillary
  def meameConsoleLog(s: String): F[Unit] =
    httpClient.expect[String](POST(s, buildUri("aux/logmsg"))).void
}
