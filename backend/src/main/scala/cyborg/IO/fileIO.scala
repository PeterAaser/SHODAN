package cyborg

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import cats.effect.IO
import fs2._

import java.io.File
import java.nio.file.{ Path, Paths }

/**
  Currently not in use, but might be useful in order to parse MCS data
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


  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  def sortFilesByDate(files: List[File]) =
    files.map(_.getName).map(DateTime.parse(_, fmt)).sorted


  def getNewestFilename: String =
    sortFilesByDate(getListOfFiles("/home/peter/MEAMEdata"))
      .head.toString(fmt)


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
