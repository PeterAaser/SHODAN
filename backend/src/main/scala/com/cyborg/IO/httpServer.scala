package com.cyborg

import io.circe.literal._
import io.circe.generic.auto._
import io.circe.syntax._

import cats.effect.IO
import org.http4s.server.Server
import scala.concurrent.ExecutionContext

import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.`Cache-Control`
import org.http4s.CacheDirective.`no-cache`
import org.http4s.client.blaze._
import org.http4s.Uri
import org.http4s.server.blaze.BlazeBuilder

import fs2._

object httpServer {

  import params.http.SHODANserver._
  import java.net.InetSocketAddress

  import httpCommands._

  def SHODANservice(commands: Sink[IO,UserCommand]): HttpService[IO] = {

    def cmd(command: UserCommand): IO[Unit] =
      Stream.emit(command).covary[IO].through(commands).run

    HttpService {
      case req @ POST -> Root / "connect" => {
        println("connect. WARNING DOES THE SAME AS STARTMEAME")
        for {
          emit <- cmd(StartMEAME)
          resp <- Ok("Connected")
        } yield (resp)
      }
      case req @ POST -> Root / "stop" => {
        println("stop")
        for {
          emit <- cmd(StopMEAME)
          resp <- Ok("Stopped")
        } yield (resp)
      }
      case req @ POST -> Root / "start" => {
        println("stop")
        for {
          emit <- cmd(StartMEAME)
          resp <- Ok("Stopped")
        } yield (resp)
      }
      case req @ POST -> Root / "agent" => {
        println("stop")
        for {
          emit <- cmd(AgentStart)
          resp <- Ok("Stopped")
        } yield (resp)
      }
      case req @ POST -> Root / "wf" => {
        println("waveform")
        for {
          emit <- cmd(AgentStart)
          resp <- Ok("Stopped")
        } yield (resp)
      }
    }
  }

  def SHODANserver(commands: Sink[IO,UserCommand]): IO[Server[IO]] = {
    import backendImplicits._
    val service = SHODANservice(commands)
    val builder = BlazeBuilder[IO].bindHttp(8080).mountService(service).start
    builder
  }
}

object httpCommands {

  trait UserCommand
  case object StartMEAME extends UserCommand
  case object StopMEAME extends UserCommand

  case object AgentStart extends UserCommand
  case object WfStart extends UserCommand
  case object StartWaveformVisualizer extends UserCommand

  case object ConfigureMEAME extends UserCommand

  case class RunFromDB(experimentId: Int) extends UserCommand
  case class StoreToDB(comment: String) extends UserCommand

}
