package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.io.tcp._
import fs2.util.syntax._

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import scala.language.higherKinds

import spinoco.fs2.http
import http._
import http.websocket._
import spinoco.protocol.http.Uri.Path

import spinoco.protocol.http.header._
import spinoco.protocol.http.Uri
import spinoco.protocol.http._
import spinoco.protocol.http.header.value._

object httpIO {

  import backendImplicits._

  // hardcoded
  val ip = "129.241.201.110"
  val port = "8888" // we're not an open server, so we don't use the regular http port.
  val baseUri = s"${ip}:${port}"
  val uri = Uri.http(ip, port=8888, path="/")

  // hardcoded
  val samplerate = 40000
  val segmentLength = 100

  implicit val StringCodec = scodec.codecs.utf8_32
  import spinoco.fs2.http.body.BodyEncoder


  def connectDAQrequest[F[_]](
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
    Does not handle failure at all

    note to self:
    Figuring out a good way to handle failure is a TODO
    Thinking of using a Stream[Either[FailureReason,Unit]] ???
    Not asking for help, this is a good opportunity to figure out Free,
    effects and all that, besides it's not useful to handle failure at the moment
   */
  def startMEAMEServer(samplerate: Int, segmentLength: Int, specialChannels: List[Int]): Task[Option[Boolean]] = {
    val clientTask: Task[HttpClient[Task]] = http.client[Task]()
    val uhh = clientTask flatMap { client =>
      (for {
        _ <- client.request(sayHello)
        MEAMEconfigurationResponse <- client.request(connectDAQrequest(samplerate, segmentLength, specialChannels))
        MEAMEstartResponse         <- client.request(startDAQrequest)
       } yield
         {
           val ret = (List(MEAMEconfigurationResponse.header.status, MEAMEstartResponse.header.status)
              .map(_ == HttpStatusCode.Ok).foldLeft(true)(_&&_))
           println(s"from our http requests we got $ret")
           ret
         }).runLast
    }
    uhh
    // uhh
  }
}
