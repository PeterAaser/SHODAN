package com.cyborg


object httpClient {

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
  import org.http4s.server.blaze.BlazeBuilder
  import org.http4s.circe._

  val httpClient = PooledHttp1Client[IO]()
  implicit val DAQdecoder = jsonOf[IO, DAQparams]
  implicit val regDecoder = jsonOf[IO, RegisterList]


  case class DAQparams(samplerate: Int, segmentLength: Int, selectChannels: List[Int])
  case class RegisterList(adresses: List[Int], values: List[Int])

  def connectDAQrequest(params: DAQparams): IO[String] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/connectDAQ"), params.asJson)
    httpClient.expect[String](req)
  }

  def setRegistersRequest(regs: RegisterList): IO[String] = {
    val req = POST(Uri.uri("http://129.241.201.110:8888/setreg"), regs.asJson)
    httpClient.expect[String](req)
  }


  def startDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/startDAQ")))

  def stopDAQrequest: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/stopDAQ")))

  def sayHello: IO[String] =
    httpClient.expect[String](GET(Uri.uri("http://129.241.201.110:8888/status")))


  import params.experiment._

  def startMEAMEserver: IO[String] = {
    val daqParams = DAQparams(samplerate, segmentLength, List(1,2,3))
    connectDAQrequest(daqParams).attempt flatMap {
      case Left(e) => IO.pure("Something went wrong when connecting to the DAQ")
      case Right(_) => startDAQrequest flatMap {
        resp => {
          IO.pure(s"Looks like we're good. resp was $resp")
        }
      }
    }
  }
}
