package com.cyborg

import fs2._

import cats.effect.IO
import scala.concurrent.ExecutionContext
import spinoco.fs2.http
import spinoco.protocol.http._
import http._

object httpServer {

  /**
    At the moment this is very sparsely populated.
    When CORS token issue is resolved this code will act as entrypoint for the
    web client.
    */

  import params.http.SHODANserver._

  import java.net.InetSocketAddress

  val respondOk = Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))

  import httpCommands._

  def service(commands: Sink[IO,userCommand])(request: HttpRequestHeader, body: Stream[IO,Byte])(implicit ec: ExecutionContext): Stream[IO,HttpResponse[IO]] = {

    println("\ngot request:")

    if (request.path == Uri.Path / "connect"){
      println(s"connect")
      Stream.emit(StartMEAME).covary[IO].through(commands).drain.concurrently(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else if (request.path == Uri.Path / "stop"){
      println(s"stop")
      Stream.emit(StopMEAME).covary[IO].through(commands).drain.concurrently(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else if (request.path == Uri.Path / "start"){
      println(s"start")
      Stream.emit(StartMEAME).covary[IO].through(commands).drain.concurrently(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else if (request.path == Uri.Path / "agent"){
      println(s"agent")
      Stream.emit(AgentStart).covary[IO].through(commands).drain.concurrently(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else if (request.path == Uri.Path / "wf"){
      println(s"wf")
      Stream.emit(WfStart).covary[IO].through(commands).drain.concurrently(
        Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World"))
      )
    }
    else {
      Stream.emit(HttpResponse(HttpStatusCode.Ok).withUtf8Body("Hello World")).drain
    }
  }

  def startServer(commands: Sink[IO,userCommand])(implicit ec: ExecutionContext): IO[Unit] = {
    println(s"Starting server at port $SHODANserverPort")
    import backendImplicits._
    http.server(new InetSocketAddress("127.0.0.1", SHODANserverPort))(service(commands)).run
  }
}

object httpCommands {

  trait userCommand
  case object StartMEAME extends userCommand
  case object StopMEAME extends userCommand

  case object AgentStart extends userCommand
  case object WfStart extends userCommand
  case object StartWaveformVisualizer extends userCommand

  case object ConfigureMEAME extends userCommand

  case class RunFromDB(experimentId: Int) extends userCommand
  case class StoreToDB(comment: String) extends userCommand


}
