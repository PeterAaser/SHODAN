package com.cyborg

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

  val httpClient = PooledHttp1Client[IO]()
  implicit val DAQdecoder = jsonOf[IO, DAQparams]
  implicit val regDecoder = jsonOf[IO, RegisterList]


  case class DAQparams(samplerate: Int, segmentLength: Int, selectChannels: List[Int])
  case class RegisterList(adresses: List[Int], values: List[Int])


  def connectDAQrequest(params: DAQparams): IO[String] = {
    if(params.samplerate > 1000){
      println(Console.YELLOW + "[WARN] samplerate possibly too high for MEAME2 currently" + Console.RESET)
    }
    val req = POST(Uri.uri("http://129.241.201.110:8888/DAQ/connect"), params.asJson)
    httpClient.expect[String](req)
  }

  def setRegistersRequest(regs: RegisterList): IO[String] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/DSP/setreg"), regs.asJson)
    httpClient.expect[String](req)
  }


  def startDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/DAQ/start")))

  def stopDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/stopDAQ")))

  def sayHello: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/status")))

  def dspTest: IO[String] =
    httpClient.expect[String](POST(Uri.uri("http://129.241.201.110:8888/DSP/dsptest")))

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
