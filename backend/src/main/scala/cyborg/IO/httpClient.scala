package cyborg

import cats.effect.IO
import cats.effect._
import cats.implicits._

import org.http4s._
import org.http4s.client._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.client.blaze._
import org.http4s.Uri
import org.http4s.circe._

import io.circe.literal._
import io.circe.generic.auto._
import io.circe.syntax._


import utilz._

object HttpClient {

  import DspRegisters._

  case class DAQparams(samplerate: Int, segmentLength: Int, selectChannels: List[Int])
  case class DspFuncCall(func: Int, args: List[(Int, Int)])
  object DspFuncCall {
    def apply(func: Int, args: (Int,Int)*): DspFuncCall = {
      DspFuncCall(func, args.toList)
    }
  }

  // don't have to read the docs if you just make an ugly hack
  case class DspFCS(func: Int, argAddrs: List[Int], argVals: List[Int])
  implicit val DspFCSCodec = jsonOf[IO, DspFCS]
  import io.circe.{ Encoder, Json }
  implicit val encodeFoo: Encoder[DspFuncCall] = new Encoder[DspFuncCall] {
    final def apply(a: DspFuncCall): Json = {
      val (addrs, words) = a.args.unzip
      DspFCS(a.func, addrs, words).asJson
    }
  }

  val httpClient = PooledHttp1Client[IO]()
  implicit val regSetCodec = jsonOf[IO, RegisterSetList]
  implicit val DAQdecoder = jsonOf[IO, DAQparams]
  implicit val regReadCodec = jsonOf[IO, RegisterReadList]
  implicit val regReadRespCodec = jsonOf[IO, RegisterReadResponse]
  implicit val dspCallCodec = jsonOf[IO, DspFuncCall]

  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // DSP
  def setRegistersRequest(regs: RegisterSetList): IO[String] =
  {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/write"), regs.asJson)
    httpClient.expect[String](req)
  }


  def readRegistersRequest(regs: RegisterReadList): IO[RegisterReadResponse] =
  {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/read"), regs.asJson)
    httpClient.expect[RegisterReadResponse](req)
  }


  def dspCall(call: Int, args: (Int,Int)*): IO[String] =
  {
    val funcCall = DspFuncCall(call, args.toList)
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/call"), funcCall.asJson)
    httpClient.expect[String](req)
  }



  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // DAQ
  def connectDAQrequest(params: DAQparams): IO[String] =
  {
    if(params.samplerate > 1000){
      say(Console.YELLOW + "[WARN] samplerate possibly too high for MEAME2 currently" + Console.RESET)
    }
    val req = POST(Uri.uri("http://129.241.201.110:8888/DAQ/connect"), params.asJson)
    httpClient.expect[String](req)
  }

  def startDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/DAQ/start")))

  def stopDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/DAQ/stop")))



  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  // Auxillary
  def meameConsoleLog(s: String): IO[String] =
    httpClient.expect[String](POST(Uri.uri("http://129.241.201.110:8888/aux/logmsg"), s))


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
