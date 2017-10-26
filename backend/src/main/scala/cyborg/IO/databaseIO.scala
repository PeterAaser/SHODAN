package cyborg

import cats.effect.Effect
import cats.effect._
import fs2._
import fs2.Stream._
import cats.effect.IO
import fs2.async.mutable.Queue
import java.nio.file.Paths
import scala.concurrent.ExecutionContext
import utilz._
import com.github.nscala_time.time.Imports._
import backendImplicits._

import scala.language.higherKinds

import doobIO._

object databaseIO {

  /**
    Gets a resource URI from the database and reads said info
    */
  def dbChannelStream(experimentId: Int)(implicit ec: ExecutionContext): Stream[IO, Int] = {
    Stream.eval(doobIO.getExperimentDataURI(experimentId)) flatMap { (data: DataRecording) =>
      val reader = data.resourceType match {
        case CSV => fileIO.readCSV[IO](data.resourcePath)
        case GZIP => fileIO.readGZIP[IO](data.resourcePath)
      }
      reader
    }
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
