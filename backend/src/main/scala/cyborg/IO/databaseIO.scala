package cyborg

import cats.effect._
import fs2._
import fs2.Stream._
import cats.effect.IO
import scala.concurrent.ExecutionContext


import doobIO._

object databaseIO {

  /**
    Gets a resource URI from the database and reads said info
    */
  def dbChannelStream(experimentId: Int)(implicit ec: ExecutionContext): (IO[Int], Stream[IO, Int]) = {
    val data = Stream.eval(doobIO.getExperimentDataURI(experimentId)) flatMap { (data: DataRecording) =>
      val reader = data.resourceType match {
        case CSV => fileIO.readCSV[IO](data.resourcePath)
        case GZIP => fileIO.readGZIP[IO](data.resourcePath)
      }
      reader
    }

    val params = doobIO.getExperimentParams(experimentId).map(_.segmentLength)
    (params, data)

  }

  /**
    Sets up the database stuff and returns a sink for recorded data
    */
  def createRecordingSink(comment: String): IO[Sink[IO,Int]] = {
    fileIO.writeCSV[IO] flatMap{ case(path, sink) =>
      insertNewExperiment(path, comment).map(_ => sink)
    }
  }
}
