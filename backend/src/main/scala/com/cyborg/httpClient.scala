package com.cyborg

import fs2._
import fs2.Stream._

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

    println(json)

    HttpRequest.post[F,String](Uri.http(ip, port=8888, path="/DAQ/connect"), json)(BodyEncoder.utf8String)
  }


  def sayHello[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/status"))


  def startDAQrequest[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/DAQ/start"))


  def stopDAQrequest[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/DAQ/stop"))


  /**
    Does what it says on the tin

    Does not handle failure at all

    note to self:
    Figuring out a good way to handle failure is a TODO
    Thinking of using a Stream[Either[FailureReason,Unit]] ???
    Not asking for help, this is a good opportunity to figure out Free,
    effects and all that, besides it's not useful to handle failure at the moment
   */
  def startMEAMEServer(
    samplerate: Int,
    segmentLength: Int,
    specialChannels: List[Int]): Task[Option[Boolean]] =
  {

    val clientTask: Task[HttpClient[Task]] = http.client[Task]()
    val connectDAQrequest = createConnectDAQrequest[Task](samplerate, segmentLength, specialChannels)

    val requestTask = clientTask flatMap { client =>
      {
        val requestTask = for {
          _ <- client.request(sayHello)
          confResponse  <- client.request(connectDAQrequest)
          startResponse <- client.request(startDAQrequest)
        }
        yield
        {
          val ret =
            (List(confResponse.header.status, startResponse.header.status)
               .map(_ == HttpStatusCode.Ok).foldLeft(true)(_&&_))

          println(s"MEAME responded with $ret")
          ret
        }
        requestTask.runLast
      }
    }
    requestTask
  }
}
