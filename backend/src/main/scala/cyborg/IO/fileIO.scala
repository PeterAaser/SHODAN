package cyborg

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import cats.effect._
import cats.effect.IO
import fs2._

import java.io.File
import java.nio.file.{ Path, Paths }

import scala.concurrent.ExecutionContext
/**
  Now in use, yay!

  Dataformat: CSV with data in segmentLength segments. KISS
  */
object fileIO {

  def getListOfFiles(dir: String): List[File] =
    (new File(dir)).listFiles.filter(_.isFile).toList

  def getListOfFiles(dir: Path): List[File] =
    dir.toFile().listFiles.filter(_.isFile).toList


  def getListOfFolders(dir: String): List[File] =
    (new File(dir)).listFiles.filter(_.isDirectory).toList

  def getListOfFolders(dir: Path): List[File] =
    dir.toFile().listFiles.filter(_.isDirectory).toList


  val fmt = DateTimeFormat.forPattern("dd.MM.yyyy, HH:mm:ss")
  def timeString = DateTime.now().toString(fmt)
  def getTimeString: IO[String] = IO {
    DateTime.now().toString(fmt)
  }


  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  def sortFilesByDate(files: List[File]) =
    files.map(_.getName).map(DateTime.parse(_, fmt)).sorted


  def getNewestFilename: String =
    sortFilesByDate(getListOfFiles("/home/peter/MEAMEdata"))
      .head.toString(fmt)


  // TODO might be perf loss to go from Array to List and all that
  def readCSV[F[_]: Effect](filePath: Path): Stream[F,Int] = {
    val reader = io.file.readAll[F](filePath, 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .through(_.map{ csvLine => csvLine.split(",").map(_.toInt).toList})
      .through(utilz.chunkify)

    reader
  }

  def readGZIP[F[_]: Effect](filePath: Path)(implicit ec: ExecutionContext): Stream[F,Int] = ???


  /**
    Creates a new file and returns the path, plus a sink for writing to said file
    */
  def writeCSV[F[_]: Effect]: IO[(Path, Sink[F,Int])] = {

    import params.StorageParams.toplevelPath

    getTimeString map { s =>
      val path: Path = Paths.get(toplevelPath + s)
      val sink: Sink[F,Int] = _
        .through(utilz.vectorize(1000))
        .through(_.map(_.mkString("",", ", "\n")))
        .through(text.utf8Encode)
        .through(io.file.writeAll(path))

      (path, sink)
    }
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
}
