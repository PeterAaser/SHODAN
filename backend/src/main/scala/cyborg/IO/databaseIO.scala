package cyborg

import cats.effect._
import fs2._
import fs2.Stream._
import cats.effect.IO
import java.nio.file.Path
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext

import cats.effect.IO

import doobIO._

object databaseIO {


  // haha nice meme dude!
  val superdupersecretPassword = "memes"

  import doobie.imports._
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:memestorage",
    "postgres",
    s"$superdupersecretPassword"
  )


  /**
    Gets a resource URI from the database and reads said info
    */
  def dbChannelStream(experimentId: Int)(implicit ec: ExecutionContext): Stream[IO, Int] = {
    println(s"making stream for experiment $experimentId")

    val data = Stream.eval(doobIO.getExperimentDataURI(experimentId.toLong)).transact(xa) flatMap { (data: DataRecording) =>
      println(s"got data $data")
      val reader = data.resourceType match {
        case CSV => fileIO.readCSV[IO](data.resourcePath)
        case GZIP => fileIO.readGZIP[IO](data.resourcePath)
      }
      reader
    }


    data
  }


  def dbGetParams(experimentId: Int): IO[Int] = {
    val params = doobIO.getExperimentParams(experimentId.toLong)
      .transact(xa)
      .map(_.segmentLength)

    params
  }


  /**
    Sets up the database stuff and returns a sink for recorded data
    */
  def createRecordingSink(comment: String): IO[Sink[IO,Int]] = {
    fileIO.writeCSV[IO] flatMap{ case(path, sink) =>
      insertNewExperiment(path, comment)
        .transact(xa)
        .map(_ => sink)
    }
  }


  def getAllExperimentIds(): IO[List[Long]] =
    doobIO.getAllExperiments.transact(xa)

  def getAllExperimentUris(): IO[List[Path]] =
    doobIO.getAllExperimentUris.transact(xa)

  def insertOldExperiment(comment: String, timestamp: DateTime, uri: String): IO[Unit] =
    doobIO.insertOldExperiment(comment, timestamp, uri).transact(xa)
}
