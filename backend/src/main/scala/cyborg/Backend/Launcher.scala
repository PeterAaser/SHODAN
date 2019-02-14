package cyborg.backend


import fs2._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.Client
import scala.concurrent.duration._

import cyborg._
import scala.util.Random
import utilz._

import cats._
import cats.effect._
import cats.implicits._

import scala.concurrent.ExecutionContext

object Launcher extends IOApp {

  val client = BlazeClientBuilder[IO](ExecutionContext.global).resource

  def run(args: List[String]): IO[ExitCode] = {                                                                                                                                                                                                                                                                                                               say("wello")

    client.use{ c =>
      Assemblers.startSHODAN(new MEAMEHttpClient[IO](c)).compile.drain.as(ExitCode.Success)
    }


    // val testFile = new java.io.File("/home/peteraa/datateknikk/SHODAN/MEAdata/big.csv").toPath()
    // cyborg.io.files.fileIO.convertMcsCsv[IO](testFile).compile.drain.as(ExitCode.Success)

    // IO.unit.as(ExitCode.Success)
  }
}
