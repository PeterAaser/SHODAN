package cyborg

import cats.effect.IO

import doobie.imports._

import fs2._

import com.github.nscala_time.time.Imports._
import java.nio.file.{ Path, Paths }

object doobIO {

  // haha nice meme dude!
  val superdupersecretPassword = "meme"

  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:memestorage",
    "postgres",
    s"$superdupersecretPassword"
  )

  case class ExperimentInfo(id: Long, timestamp: DateTime, comment: Option[String])
  case class DataRecording(resourcePath: Path, resourceType: FileEncoding)
  case object DataRecording {
    def apply(rpath: String, rtype: String): DataRecording = DataRecording(Paths.get(rpath), parseResourceType(rtype))
  }

  // TODO lets not use Int for duration
  case class ExperimentParams(sampleRate: Int, segmentLength: Int, duration: Option[Int])


  sealed trait FileEncoding
  case object CSV extends FileEncoding
  case object GZIP extends FileEncoding

  def parseResourceType(s: String): FileEncoding = s match {
    case "CSV" => CSV
    case "gzip" => GZIP
    case _ => {
      // cba with Option here...
      println("uh oh, you fucking dunce")
      GZIP
    }
  }


  def getExperimentParams(experimentId: Long): IO[ExperimentParams] = {
    sql"""
        SELECT sampleRate, segmentLength
        FROM experimentParams
        WHERE experimentId = $experimentId
      """.query[ExperimentParams].unique.transact(xa)
  }


  // probably explodes lol
  def getExperimentDataURI(experimentId: Long): IO[DataRecording] = {
    val hurr = sql"SELECT resourcePath, resourceType FROM experimentInfo WHERE experimentId = $experimentId"
      .query[(String, String)].unique.transact(xa)
    hurr.map{ case(λ, µ) => DataRecording(λ, µ) }
  }


  def insertNewExperiment(path: Path, comment: String="No comment atm"): IO[Long] = {

    val insertExperiment = sql"INSERT INTO experimentInfo comment VALUES $comment".update.run.transact(xa)

    insertExperiment flatMap { id =>
      insertDataRecording(id, path.toString()) flatMap { _ =>
        insertParams(id).map(_ => id)
      }}
  }


  def insertDataRecording(id: Long, path: String): IO[Int] = {
    import params.StorageParams._
    sql"INSERT INTO dataRecording (experimentId, resourcePath, resourceType) VALUES ($id, $path, $storageType)".update.run.transact(xa)
  }

  def insertParams(id: Long): IO[Int] = {
    import params.experiment._
    sql"INSERT INTO experimentParams (sampleRate, segmentLength) VALUES ($samplerate, $segmentLength)".update.run.transact(xa)
  }

  def getExperimentsByMEA(MEAid: Int): Stream[IO, String] = ???


  def insertOldExperiment(comment: Option[String], timestamp: DateTime): IO[Long] = {
    val comment_ = comment.getOrElse("no comment")
    val fmt = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss")
    val timeString = timestamp.toString(fmt)

    println(s"inserting old experiment with comment $comment_, date: $timeString::timestamp")

    val op = for {
      _ <- sql"set datestyle = dmy".update.run
      _ <- sql"INSERT INTO experimentInfo (comment, experimentTimeStamp) VALUES ($comment_, $timeString::timestamp)".update.run
      id <- sql"select lastval()".query[Long].unique
    } yield (id)
    op.transact(xa)
  }

}
