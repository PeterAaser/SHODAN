package com.cyborg

import fs2._
import fs2.util.syntax._

import spinoco.fs2.http
import spinoco.protocol.http.header._
import spinoco.protocol.http._
import spinoco.protocol.http.header.value._
import spinoco.fs2.http.routing._
import shapeless.{HNil, ::}
import http.websocket._
import http._

object httpServer {

  import backendImplicits._

  // hardcoded
  val SHODANserverIP = "127.0.0.1"
  val SHODANserverPort = 9998
  import java.net.InetSocketAddress

  val respondOk: Stream[Task,HttpResponse[Task]] =
    Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))


  def service(commands: Sink[Task,Int])(request: HttpRequestHeader, body: Stream[Task,Byte]): Stream[Task,HttpResponse[Task]] = {
    println("Got reply")

    // val theShow =
    if (request.path != Uri.Path / "echo"){
      Stream.emit(1).through(commands).mergeDrainL(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else {
      Stream.emit(1).through(commands).mergeDrainL(
      Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
  }

  def startServer(commands: Sink[Task,Int]): Task[Unit] = {
    println(s"Starting server at port $SHODANserverPort")
    http.server(new InetSocketAddress("127.0.0.1", SHODANserverPort))(service(commands)).run
  }
}
