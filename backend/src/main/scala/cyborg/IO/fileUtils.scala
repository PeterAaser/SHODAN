package cyborg.io.files
import cats.Functor
import cats._
import cats.implicits._
import cyborg._
import fs2.concurrent.Topic

import org.joda.time._
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import cats.effect._
import fs2._
import utilz._

import scala.concurrent.duration._

import java.io.File
import java.nio.file.{ Path, Paths }
import cyborg.backendImplicits._


object fileUtils {

  import params.StorageParams.toplevelPath

  def getListOfFiles(dir : String): List[File] = (new File(dir)).listFiles.filter(_.isFile).toList
  def getListOfFiles(dir : Path): List[File]   = dir.toFile().listFiles.filter(_.isFile).toList
  def getListOfFolders(dir : String): List[File] = (new File(dir)).listFiles.filter(_.isDirectory).toList
  def getListOfFolders(dir : Path): List[File]   = dir.toFile().listFiles.filter(_.isDirectory).toList

  def recursiveFindFiles(path: Path, fileType: String): List[Path] = {
    val filesHere = getListOfFiles(path)
    val foldersHere = getListOfFolders(path)

    foldersHere.map(folder => recursiveFindFiles(folder.toPath(), fileType)).flatten :::
    filesHere
      .filter(_.toString().contains("."))
      .filter(fp => fp.toString().split('.').last.equals(fileType))
      .map(_.toPath())
  }

  def dateTimeString(implicit dtfmt: DateTimeFormatter) = DateTime.now().toString(dtfmt)
  def getDateTimeString[F[_]: Sync](implicit dtfmt: DateTimeFormatter): F[String] = Sync[F].delay {
    DateTime.now().toString(dtfmt)
  }

  def sortFilesByDate(files: List[File])(implicit dtfmt: DateTimeFormatter, ord: Ordering[DateTime]) =
    files.map(_.getName).map(DateTime.parse(_, dtfmt)).sorted

  def getNewestFilename(implicit dtfmt: DateTimeFormatter, ord: Ordering[DateTime]) : String =
    sortFilesByDate(getListOfFiles(toplevelPath))
      .head.toString(dtfmt)

  val tfmt = DateTimeFormat.forPattern("HH:mm:ss")
  def timeString = DateTime.now().toString(tfmt)
  def getTimeString: IO[String] = IO {
    DateTime.now().toString(tfmt)
  }

}
