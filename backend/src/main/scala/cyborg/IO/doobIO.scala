package cyborg.io.database
import cyborg._

import doobie.imports._
import doobie.postgres.imports._
import org.joda.time._

import fs2._
import scala.concurrent.duration.FiniteDuration

import utilz._

import java.nio.file.{ Path, Paths }


object DoobieQueries {

  case class ExperimentInfo(id: Int,
                            startTime: DateTime,
                            finishTime: Option[DateTime],
                            comment: String)

  case class DataRecording(resourcePath: Path,
                           resourceType: FileEncoding)

  case object DataRecording { def apply(rpath: String, rtype: String): DataRecording = DataRecording(Paths.get(rpath), parseResourceType(rtype)) }

  case class ExperimentParams(id: Int, samplerate: Int, segmentLength: Int)

  def checkQuery[A](q: Query0[A]): Unit = ()
  // def checkQuery[A](q: Query0[A]): Unit = q.check.unsafeRunSync()

  sealed trait FileEncoding
  case object CSV extends FileEncoding
  case object GZIP extends FileEncoding

  def parseResourceType(s: String): FileEncoding = s match {
    case "CSV" => CSV
    case "gzip" => GZIP
    case _ => {
      // cba with Option here...
      say("uh oh, you fucking dunce, no encoding specified! Going with GZIP for that NYI deadlock")
      GZIP
    }
  }


  def getNewestExperimentId: ConnectionIO[Int] = {
    val q = sql"""
      SELECT (id)
      FROM experimentInfo
      ORDER BY startTime DESC
      LIMIT 1
    """.query[Int]

    checkQuery(q)
    q.unique
  }


  def getExperimentParams(experimentId: Int): ConnectionIO[ExperimentParams] = {
    val q = sql"""
        SELECT *
        FROM experimentParams
        WHERE experimentId = $experimentId
      """.query[ExperimentParams]

    checkQuery(q)
    q.unique

  }


  // probably explodes lol
  def getExperimentDataURI(experimentId: Int): ConnectionIO[DataRecording] = {
    val q = sql"""
      SELECT *
      FROM dataRecording
      WHERE experimentId = $experimentId
    """.query[(Int, String, String)]

    checkQuery(q)
    q.unique.map{ case(_, λ, µ) => DataRecording(λ, µ) }
  }


  def getExperimentInfo(experimentId: Int): ConnectionIO[ExperimentInfo] = {
    val q = sql"""
      SELECT *
      FROM experimentInfo
      WHERE id = $experimentId
    """.query[ExperimentInfo]

    checkQuery(q)
    q.unique

  }


  def insertNewExperiment(path: Path, comment: String = "No comment atm", conf: Setting.ExperimentSettings): ConnectionIO[Int] = {
    say("insert new experiment")
    import doobie.implicits._

    val insertExperiment = for {
      _ <- sql"INSERT INTO experimentInfo (comment) VALUES ($comment)".update.run
      id <- sql"select lastval()".query[Int].unique
    } yield (id)

    insertExperiment flatMap { id =>
      say(s"inserted experiment, the id was $id")
      insertDataRecording(id, path.toString()) flatMap { _ =>
        insertParams(id, conf).map(_ => id)
      }}
  }

  def finalizeExperiment(id: Int): ConnectionIO[Int] = {
    val q = sql"""
      UPDATE experimentInfo
      SET finishTime = now()
      WHERE id = $id
    """

    q.update.run
  }

  import params.StorageParams._

  def insertDataRecording(id: Int, path: String): ConnectionIO[Int] = {
    val q = sql"""
      INSERT INTO dataRecording (experimentId, resourcePath, resourceType)
      VALUES (${id.toInt}, $path, $storageType)
    """

    q.update.run
  }


  def insertParams(id: Int, conf: Setting.ExperimentSettings): ConnectionIO[Int] = {
    import conf._
    val q = sql"""
      INSERT INTO experimentParams (experimentid, sampleRate, segmentLength)
      VALUES ($id, $samplerate, $segmentLength)
    """

    q.update.run
  }


  def getExperimentsByMEA[F[_]](MEAid: Int): Stream[F, Int] = {
    say("warning, calling NYI method, might clog")
    ???
  }


  // TODO Test properly
  def insertOldExperiment(comment: String, timestamp: DateTime, uri: String): ConnectionIO[Unit] = {

    import java.sql.Timestamp
    val ts = new Timestamp(timestamp.getMillis())

    say(s"inserting old experiment with comment $comment, date: $ts")
    val insertInfo = sql"""
      INSERT INTO experimentInfo (comment, startTime)
      VALUES ($comment, $ts)
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


  def getAllExperiments(): ConnectionIO[List[Int]] =
    sql"SELECT id FROM experimentInfo"
      .query[Int].to[List]


  def getAllExperimentUris(): ConnectionIO[List[Path]] =
    sql"SELECT resourcePath from dataRecording"
      .query[String].to[List]
      .map(_.map(Paths.get(_)))


  import doobie.imports.Meta
  import java.sql.Timestamp
  import org.joda.time.{ DateTime, Instant }


  // TODO just moved nxmap to xmap. YOLO
  implicit val InstantMeta: Meta[Instant] = Meta[Timestamp].xmap(
    (t: Timestamp) => new Instant(t.getTime),
    (i: Instant) => new Timestamp(i.getMillis)
  )

  implicit val DateTimeMeta: Meta[DateTime] = Meta[Timestamp].xmap(
    (t: Timestamp) => new DateTime(t.getTime),
    (d: DateTime) => new Timestamp(d.getMillis)
  )


  def experimentsInInterval(interval: Interval): ConnectionIO[List[Int]] = {
    val q = sql"""
      SELECT id FROM ExperimentInfo
      WHERE (startTime  BETWEEN ${interval.getStart()}
                            AND ${interval.getEnd()})
        AND (finishTime BETWEEN ${interval.getStart()}
                            AND ${interval.getEnd()})
      """.query[Int]

    q.to[List]
  }
}
