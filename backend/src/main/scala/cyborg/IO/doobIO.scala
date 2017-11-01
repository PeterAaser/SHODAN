package cyborg

import cats.effect.IO
import doobie.imports._

import fs2._

import com.github.nscala_time.time.Imports._
import java.nio.file.{ Path, Paths }

object doobIO {

  val YOLO = databaseIO.xa.yolo
  import doobie._, doobie.implicits._
  import YOLO._

  case class ExperimentInfo(id: Long, timestamp: DateTime, comment: Option[String])
  case class DataRecording(resourcePath: Path, resourceType: FileEncoding)
  case object DataRecording {
    def apply(rpath: String, rtype: String): DataRecording = DataRecording(Paths.get(rpath), parseResourceType(rtype))
  }

  // TODO lets not use Int for duration
  case class ExperimentParams(id: Long, sampleRate: Int, segmentLength: Int, duration: Int)


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


  def getExperimentParams(experimentId: Long): ConnectionIO[ExperimentParams] = {

    sql"""
        SELECT *
        FROM experimentParams
        WHERE experimentId = $experimentId
      """.query[ExperimentParams].check.unsafeRunSync()


    sql"""
        SELECT *
        FROM experimentParams
        WHERE experimentId = $experimentId
      """.query[ExperimentParams].unique
  }


  // probably explodes lol
  def getExperimentDataURI(experimentId: Long): ConnectionIO[DataRecording] = {
    println("get exp uri")


    sql"""
      SELECT *
      FROM dataRecording
      WHERE experimentId = $experimentId
    """.query[(Long, String, String)].check.unsafeRunSync()


    sql"""
      SELECT *
      FROM dataRecording
      WHERE experimentId = $experimentId
    """.query[(Long, String, String)].unique
      .map{ case(_, λ, µ) => DataRecording(λ, µ) }
  }


  def insertNewExperiment(path: Path, comment: String = "No comment atm"): ConnectionIO[Long] = {

    val insertExperiment = sql"INSERT INTO experimentInfo comment VALUES $comment".update.run

    insertExperiment flatMap { id =>
      insertDataRecording(id, path.toString()) flatMap { _ =>
        insertParams(id).map(_ => id)
      }}
  }

  import params.experiment._
  import params.StorageParams._

  def insertDataRecording(id: Long, path: String): ConnectionIO[Int] =
    sql"""
      INSERT INTO dataRecording (experimentId, resourcePath, resourceType)
      VALUES ($id, $path, $storageType)
    """.update.run


  def insertParams(id: Long): ConnectionIO[Int] =
    sql"""
      INSERT INTO experimentParams (sampleRate, segmentLength)
      VALUES ($samplerate, $segmentLength)
    """.update.run


  def getExperimentsByMEA(MEAid: Int): Stream[IO, Long] = ???


  // TODO: Test properly
  def insertOldExperiment(comment: String, timestamp: DateTime, uri: String): ConnectionIO[Unit] = {
    val fmt = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss")
    val timeString = timestamp.toString(fmt)

    println(s"inserting old experiment with comment $comment, date: $timeString::timestamp")
    val insertInfo = sql"""
      INSERT INTO experimentInfo (comment, experimentTimeStamp)
      VALUES ($comment, $timeString::timestamp)
    """

    def insertPlaceholderParams(id: Int) = sql"""
      INSERT INTO experimentparams (experimentId, sampleRate, segmentLength, duration)
      VALUES ($id, 10000, 1000, -1)
    """

    for {
      _  <- sql"set datestyle = dmy".update.run
      id <- insertInfo.update.run
      _  <- insertPlaceholderParams(id).update.run
      _  <- insertDataRecording(id, uri)
    } yield ()
  }


  def getAllExperiments(): ConnectionIO[List[Long]] =
    sql"SELECT id FROM experimentInfo"
      .query[Long].list


  def getAllExperimentUris(): ConnectionIO[List[Path]] =
    sql"SELECT resourcePath from dataRecording"
      .query[String].list
      .map(_.map(Paths.get(_)))
}
