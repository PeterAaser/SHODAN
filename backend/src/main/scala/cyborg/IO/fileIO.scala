package cyborg.io.files
import cyborg._
import fs2.async.mutable.Topic

import org.joda.time._
import org.joda.time.format.DateTimeFormat
import cats.effect._
import cats.effect.IO
import fs2._
import utilz._

import scala.concurrent.duration._

import java.io.File
import java.nio.file.{ Path, Paths }

import scala.concurrent.ExecutionContext
/**
  Now in use, yay!

  Dataformat: CSV with data in segmentLength segments. KISS
  */
object fileIO {

  import params.StorageParams.toplevelPath

  def getListOfFiles(dir: String): List[File] =
    (new File(dir)).listFiles.filter(_.isFile).toList

  def getListOfFiles(dir: Path): List[File] =
    dir.toFile().listFiles.filter(_.isFile).toList


  def getListOfFolders(dir: String): List[File] =
    (new File(dir)).listFiles.filter(_.isDirectory).toList

  def getListOfFolders(dir: Path): List[File] =
    dir.toFile().listFiles.filter(_.isDirectory).toList


  val dtfmt = DateTimeFormat.forPattern("dd.MM.yyyy, HH:mm:ss")
  def dateTimeString = DateTime.now().toString(dtfmt)
  def getDateTimeString: IO[String] = IO {
    DateTime.now().toString(dtfmt)
  }


  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  def sortFilesByDate(files: List[File]) =
    files.map(_.getName).map(DateTime.parse(_, dtfmt)).sorted


  def getNewestFilename: String =
    sortFilesByDate(getListOfFiles(toplevelPath))
      .head.toString(dtfmt)


  val tfmt = DateTimeFormat.forPattern("HH:mm:ss")
  def timeString = DateTime.now().toString(tfmt)
  def getTimeStringUnsafe: String = DateTime.now().toString(tfmt)
  def getTimeString: IO[String] = IO {
    DateTime.now().toString(tfmt)
  }


  // TODO might be perf loss to go from Array to List and all that
  def readCSV[F[_]: Effect](filePath: Path)(implicit ec: EC): Stream[F,Int] = {
    val reader = fs2.io.file.readAll[F](filePath, 4096*32)
      .through(text.utf8Decode)
      .through(text.lines)
      .through(_.map{ csvLine => csvLine.split(",").map(_.toFloat.toInt).toList})
      .through(utilz.chunkify)
      .handleErrorWith{
        case e: java.lang.NumberFormatException => { say("Record done"); Stream.empty}
        case e: Exception => { say(s"very bad error ${e.printStackTrace()}"); Stream.empty }
      }

    reader
  }

  def readGZIP[F[_]: Effect](filePath: Path)(implicit ec: ExecutionContext): Stream[F,Int] = ???


  def getTimestampedFilename: IO[Path] = {
    import params.StorageParams.toplevelPath
    getDateTimeString.map(s => Paths.get(toplevelPath + s))
  }


  /**
    Creates a new file and returns the path, plus a sink for writing to said file
    */
  def writeCSV[F[_]: Effect]: IO[(Path, Sink[F,Int])] = {

    import params.StorageParams.toplevelPath

    getDateTimeString map { s =>
      val path: Path = Paths.get(toplevelPath + s)
      val sink: Sink[F,Int] = _
        .through(utilz.vectorize(1000))
        .through(_.map(_.mkString("",", ", "\n")))
        .through(text.utf8Encode)
        .through(fs2.io.file.writeAll(path))

      (path, sink)
    }
  }


  /**
    Writes events to a file
    */
  def timestampedEventWriter[F[_]: Effect](implicit ev: Timer[F]): Path => Sink[F,String] = { path =>
    val eventPath = Paths.get(path.toString() + "_events")
    s => s
      .through(timeStamp)
      .through(text.utf8Encode)
      .through(fs2.io.file.writeAll(eventPath))
  }

  /**
    Attaches an eventWriter to a stream
    */
  def attachEventWriter[F[_]: Effect : Timer, O](writer: Sink[F,String], serialize: O => String)(implicit ec: EC): Pipe[F,O,O] = {
    val writeSink: Sink[F,O] = s => s.through(_.map(serialize)).through(writer)
    s => s.observeAsync(100)(writeSink)
  }


  /**
    * Outputs a sink and path for writing an integer stream as a
    * compressed file. Use default values for deflate.
    */
  def writeCompressed[F[_]: Effect]: IO[(Path, Sink[F,Int])] = {
    import params.StorageParams.toplevelPath

    getDateTimeString map {s =>
      val path: Path = Paths.get(toplevelPath + s)
      val sink: Sink[F, Int] = _
        .through(utilz.intToBytes)
        .through(fs2.compress.deflate())
        .through(fs2.io.file.writeAll(path))

      (path, sink)
    }
  }


  /**
    * Reads a compressed binary file of integers. Use default values
    * for inflate.
    */
  def streamCompressed(filePath: String): Stream[IO,Int] = {
    import params.StorageParams.toplevelPath

    fs2.io.file.readAll[IO](Paths.get(toplevelPath + filePath), 4096)
      .through(fs2.compress.inflate())
      .through(bytesToIntsJVM)
  }


  /**
    Streams data from a file representing a single channel
    */
  def streamChannelData(filePath: String): Stream[IO,Int] = {
    fs2.io.file.readAll[IO](Paths.get(filePath), 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .filter(!_.isEmpty)
      .through(_.map(_.split(",").map(_.toInt).toList))
      .through(utilz.chunkify)
  }


  def stringToFile[F[_]: Effect](s: String, path: Path): Stream[F,Unit] = {
    Stream.emit(s).covary[F].through(text.utf8Encode).through(fs2.io.file.writeAll(path))
  }
}
