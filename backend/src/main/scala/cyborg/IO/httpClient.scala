package cyborg

import io.circe.literal._
import io.circe.generic.auto._
import io.circe.syntax._

import fs2._
import cats.effect.IO
import cats.effect._

import org.http4s._
import org.http4s.client._
import org.http4s.dsl._
import org.http4s.client.blaze._
import org.http4s.Uri
import org.http4s.circe._


object HttpClient {

  import DspComms._
  import twiddle._
  import DspRegisters._

  val httpClient = PooledHttp1Client[IO]()
  implicit val DAQdecoder = jsonOf[IO, DAQparams]
  implicit val regSetCodec = jsonOf[IO, RegisterSetList]
  implicit val regReadCodec = jsonOf[IO, RegisterReadList]
  implicit val regReadRespCodec = jsonOf[IO, RegisterReadResponse]
  implicit val StimCodec = jsonOf[IO, StimReq]
  implicit val DSPconfigCodec = jsonOf[IO, DSPconf]


  case class DAQparams(samplerate: Int, segmentLength: Int, selectChannels: List[Int])
  // case class SimpleStimReq(period: Int)
  case class StimReq(periods: List[Int])



  // DSP
  def setRegistersRequest(regs: RegisterSetList): IO[String] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/setreg"), regs.asJson)
    httpClient.expect[String](req) }

  def readRegistersRequest(regs: RegisterReadList): IO[RegisterReadResponse] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/readreg"), regs.asJson)
    httpClient.expect[RegisterReadResponse](req) }

  def stimRequest(stim: StimReq): IO[String] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/stimreq"), stim.asJson)
    httpClient.expect[String](req) }

  // Uploads bitfile etc
  def dspConnect: IO[Unit] =
    httpClient.expect[Unit](POST(Uri.uri("http://129.241.201.110:8888/DSP/connect")))

  // Configures uploaded bitfile
  def dspConfigure: IO[String] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/stimreq"), DspComms.defaultDSPconfig.asJson)
    httpClient.expect[String](req)
  }

  def dspBarf: IO[String] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/barf"))
    httpClient.expect[String](req)
  }

  def dspDebugReset: IO[String] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/reset_debug"))
    httpClient.expect[String](req)
  }


  // DAQ
  def connectDAQrequest(params: DAQparams): IO[String] = {
    if(params.samplerate > 1000){
      println(Console.YELLOW + "[WARN] samplerate possibly too high for MEAME2 currently" + Console.RESET)
    }
    val req = POST(Uri.uri("http://129.241.201.110:8888/DAQ/connect"), params.asJson)
    httpClient.expect[String](req) }

  def startDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/DAQ/start")))

  def stopDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/stopDAQ")))



  // Auxillary
  def dspStimTest: IO[String] =
    httpClient.expect[String](POST(Uri.uri("http://129.241.201.110:8888/DSP/stimtest")))

  def meameConsoleLog(s: String): IO[String] =
    httpClient.expect[String](POST(Uri.uri("http://129.241.201.110:8888/logmsg"), s))

  def dspTest: IO[String] =
    httpClient.expect[String](POST(Uri.uri("http://129.241.201.110:8888/DSP/dsptest")))

  def readRegistersDirect(regs: RegisterReadList): IO[RegisterReadResponse] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/dump"), regs.asJson)
    httpClient.expect[RegisterReadResponse](req) }


  import params.experiment._

  def startMEAMEserver: IO[String] = {
    val daqParams = DAQparams(samplerate, segmentLength, List(1,2,3))
    connectDAQrequest(daqParams).attempt flatMap {
      case Left(e) => IO.pure("Something went wrong when connecting to the DAQ")
      case Right(_) => startDAQrequest.attempt flatMap {
        case Left(e) => IO.pure(s"Something went wrong when connection to the DAQ.\n$e")
        case Right(resp) => {
          IO.pure(s"Looks like we're good. resp was $resp")
        }
      }
    }
  }
}
