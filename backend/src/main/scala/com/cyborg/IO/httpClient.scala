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

  def tryHttpWithError(
    client: HttpRequest[Task] => Stream[Task,HttpResponse[Task]],
    req: HttpRequest[Task],
    errorMsg: HttpResponse[Task] => String): Stream[Task,Either[String,Unit]] = {

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
    client: HttpClient[Task]): Stream[Task, Either[String,Unit]] =
  {
    def failureMsg(resp: HttpResponse[Task]): String = {
      s"DAQ connection failed with error code ${resp.header.status}\n" +
      s"reason was: \nFuck knows..."
    }
    val req = createConnectDAQrequest[Task](samplerate, segmentLengt, selectChannels)
    tryHttpWithError(client.request(_), req, failureMsg)
  }


  def sayHello[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/status"))

  def sayHelloT(client: HttpClient[Task]): Stream[Task, Either[String,Unit]] =
    tryHttpWithError(client.request(_), sayHello, resp => s"sayHello failed with ${resp.header.status}")



  def startDAQrequest[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/DAQ/start"))

  def startDAQrequestT(client: HttpClient[Task]): Stream[Task, Either[String,Unit]] =
    tryHttpWithError(client.request(_), startDAQrequest, resp => s"start DAQ failed with ${resp.header.status}")



  def stopDAQrequest[F[_]]: HttpRequest[F] =
    HttpRequest.get[F](Uri.http(ip, port=8888, path="/DAQ/stop"))

  def stopDAQrequestT(client: HttpClient[Task]): Stream[Task, Either[String,Unit]] =
    tryHttpWithError(client.request(_), stopDAQrequest, resp => s"stop DAQ failed with ${resp.header.status}")


  def startMEAMEServer2(
    samplerate: Int,
    segmentLength: Int,
    specialChannels: List[Int]): Stream[Task,Either[String,Unit]] =
  {
    val clientTask: Task[HttpClient[Task]] = http.client[Task]()
    for {
      client <- Stream.eval(clientTask)
      _ <- sayHelloT(client)
      _ <- connectDAQrequestT(samplerate, segmentLength, specialChannels, client)
      a <- startDAQrequestT(client)
    } yield a
  }


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

  // object FreeHttpClient {

  //   import cats.free.Free
  //   import cats.free.Free.liftF
  //   import cats.arrow.FunctionK
  //   import cats.{Id, ~>}
  //   import scala.collection.mutable
  //   import cats.data.State

  //   sealed trait HttpOp[A]
  //   case object sayHello extends HttpOp[Either[String,Unit]]
  //   case class connectDAQ(samplerate: Int,
  //                         segmentLength: Int,
  //                         selectChannels: List[Int]) extends HttpOp[Either[String,Unit]]
  //   case object startDAQ extends HttpOp[Either[String,Unit]]
  //   case object stopDAQ extends HttpOp[Either[String,Unit]]

  //   type HttpClientAction[A] = Free[HttpOp,A]

  //   def connectDAQrequest(
  //     samplerate: Int,
  //     segmentLength: Int,
  //     selectChannels: List[Int]): HttpClientAction[Either[String,Unit]] =
  //     liftF[HttpOp,Either[String,Unit]](connectDAQ(samplerate,segmentLength,selectChannels))

  //   val sayHelloRequest: HttpClientAction[Either[String,Unit]] =
  //     liftF[HttpOp,Either[String,Unit]](sayHello)

  //   val startDAQrequest: HttpClientAction[Either[String,Unit]] =
  //     liftF[HttpOp,Either[String,Unit]](startDAQ)

  //   val stopDAQrequest: HttpClientAction[Either[String,Unit]] =
  //     liftF[HttpOp,Either[String,Unit]](stopDAQ)


  //   def HttpDoer: HttpClientAction ~> Task =
  //     new (HttpClientAction ~> Task) {

  //       val clientTask: Task[HttpClient[Task]] = http.client[Task]()

  //       def apply[A](fa: HttpOp[A]): Task[A] =
  //         fa match {
  //           case connectDAQ(samplerate, segmentLength, selectChannels) =>
  //             val req = createConnectDAQrequest[Task](samplerate, segmentLength, selectChannels)
  //             val tmp = Stream.eval(clientTask) flatMap { client =>
  //               val fug = client.request(req).runLast
  //               ???
  //             }
  //             ???
  //         }
  //     }
  // }
}
