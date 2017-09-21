package com.cyborg

import fs2._
import fs2.Stream._
import cats.effect.IO
import scala.concurrent.ExecutionContext

import scala.language.higherKinds

import spinoco.fs2.http
import http._

import spinoco.protocol.http.Uri
import spinoco.protocol.http._


object httpClient {

  import backendImplicits._

  import params.http.MEAMEclient._

  val baseUri = s"${ip}:${port}"
  val uri = Uri.http(ip, port=8888, path="/")

  implicit val StringCodec = scodec.codecs.utf8_32
  import spinoco.fs2.http.body.BodyEncoder

  def tryHttpWithError(
    client: HttpRequest[IO] => Stream[IO,HttpResponse[IO]],
    req: HttpRequest[IO],
    errorMsg: HttpResponse[IO] => String): Stream[IO,Either[String,Unit]] = {

    client(req).map { response =>
      if(response.header.status == HttpStatusCode.Ok)
        Right(())
      else
        Left(errorMsg(response))
    }
  }


  def createConnectDAQrequest[F[_]](
    samplerate: Int,
    segmentLength: Int,
    selectChannels: List[Int]): HttpRequest[F] =
  {

    val json =
      "{ \"samplerate\": " + s"${samplerate}," +
        "\"segmentLength\": " + s"${segmentLength}," +
        "\"specialChannels\": " +
        s"[${selectChannels.head}" +
        ("" /: selectChannels.tail)((λ, µ) => λ + s",$µ") +
        "]}"

    HttpRequest.post[F,String](Uri.http(ip, port=8888, path="/DAQ/connect"), json)(BodyEncoder.utf8String)
  }

  def connectDAQrequestT(
    samplerate: Int,
    segmentLengt: Int,
    selectChannels: List[Int],
    client: HttpClient[IO]): Stream[IO, Either[String,Unit]] =
  {
    def failureMsg(resp: HttpResponse[IO]): String = {
      s"DAQ connection failed with error code ${resp.header.status}\n" +
      s"reason was: \nFuck knows..."
    }
    val req = createConnectDAQrequest[IO](samplerate, segmentLengt, selectChannels)
    tryHttpWithError(client.request(_), req, failureMsg)
  }


  def sayHello[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/status"))

  def sayHelloT(client: HttpClient[IO]): Stream[IO, Either[String,Unit]] =
    tryHttpWithError(client.request(_), sayHello, resp => s"sayHello failed with ${resp.header.status}")



  def startDAQrequest[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/DAQ/start"))

  def startDAQrequestT(client: HttpClient[IO]): Stream[IO, Either[String,Unit]] =
    tryHttpWithError(client.request(_), startDAQrequest, resp => s"start DAQ failed with ${resp.header.status}")



  def stopDAQrequest[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/DAQ/stop"))

  def stopDAQrequestT(client: HttpClient[IO]): Stream[IO, Either[String,Unit]] =
    tryHttpWithError(client.request(_), stopDAQrequest, resp => s"stop DAQ failed with ${resp.header.status}")


  def startMEAMEServer(implicit ec: ExecutionContext): Stream[IO,Either[String,Unit]] =
  {

    import params.experiment._

    val clientTask: IO[HttpClient[IO]] = http.client[IO]()
    for {
      client <- Stream.eval(clientTask)
      _ <- sayHelloT(client)
      _ <- connectDAQrequestT(samplerate, segmentLength, List(1,2,3), client)
      a <- startDAQrequestT(client)
    } yield a
  }
}
