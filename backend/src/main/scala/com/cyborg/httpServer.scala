package com.cyborg

import fs2._

import spinoco.fs2.http
import spinoco.protocol.http._
import http._

object httpServer {

  /**
    At the moment this is very sparsely populated.
    When CORS token issue is resolved this code will act as entrypoint for the
    web client.
    */

  import backendImplicits._
  import params.http.SHODANserver._

  import java.net.InetSocketAddress

  val respondOk: Stream[Task,HttpResponse[Task]] =
    Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))

  import httpCommands._

  def service(commands: Sink[Task,userCommand])(request: HttpRequestHeader, body: Stream[Task,Byte])
      : Stream[Task,HttpResponse[Task]] = {

    println("\ngot request:")

    if (request.path == Uri.Path / "connect"){
      println("emmitting connect command")
      Stream.emit(StartMEAME).through(commands).mergeDrainL(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else if (request.path == Uri.Path / "stop"){
      println("emitting stop command")
      Stream.emit(StopMEAME).through(commands).mergeDrainL(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else if (request.path == Uri.Path / "start"){
      println("emitting start command")
      Stream.emit(StartMEAME).through(commands).mergeDrainL(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else if (request.path == Uri.Path / "agent"){
      println("emitting agent command")
      Stream.emit(AgentStart).through(commands).mergeDrainL(
      Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else if (request.path == Uri.Path / "wf"){
      println("emitting wf command")
      Stream.emit(WfStart).through(commands).mergeDrainL(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else {
      println("no match")
      Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
    }
  }

  def startServer(commands: Sink[Task,userCommand]): Task[Unit] = {
    println(s"Starting server at port $SHODANserverPort")
    http.server(new InetSocketAddress("127.0.0.1", SHODANserverPort))(service(commands)).run
  }
}

object httpCommands {

  trait userCommand
  case object StartMEAME extends userCommand
  case object StopMEAME extends userCommand

  case object AgentStart extends userCommand
  case object WfStart extends userCommand

  case object ConfigureMEAME extends userCommand

}
