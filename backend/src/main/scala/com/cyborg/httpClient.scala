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

  implicit val tcpACG : AsynchronousChannelGroup = namedACG.namedACG("tcp")
  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(16, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(16)

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
      // val hlep = client.request(sayHello) flatMap { _ =>
      //   client.request(connectDAQrequest(samplerate, segmentLength, specialChannels)) flatMap {
      //     MEAMEconfigurationResponse => {
      //       // println(MEAMEconfigurationResponse)
      //       // true
      //       client.request(startDAQrequest) map {
      //         MEAMEstartResponse => {
      //           val resp = (List(MEAMEconfigurationResponse.header.status, MEAMEstartResponse.header.status)
      //                        .map(_ == HttpStatusCode.Ok).foldLeft(true)(_&&_))
      //           resp
      //         }
      //       }
      //     }
      //   }
      // }
      // hlep.runLast
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


  def startMEAMEServer_(samplerate: Int, segmentLength: Int, specialChannels: List[Int]): Task[Option[Boolean]] = {
    val clientTask: Task[HttpClient[Task]] = http.client[Task]()
    http.client[Task]().flatMap { client =>
      val request = HttpRequest.get[Task](uri)
      val c = client.request(request).flatMap { resp =>
        Stream.eval(resp.bodyAsString)
      }
      val f = c.runLast.map {
        _ => Some(true)
      }
      f
    }
  }

  // http.client[Task]().flatMap { client =>
  //   val request = HttpRequest.get[Task](Uri.https("github.com", "/Spinoco/fs2-http"))
  //   val c = client.request(request).flatMap { resp =>
  //     Stream.eval(resp.bodyAsString)
  //   }
  //   val f = c.runLog.map {
  //     println
  //   }
  //   f
  // }.unsafeRun()
}
