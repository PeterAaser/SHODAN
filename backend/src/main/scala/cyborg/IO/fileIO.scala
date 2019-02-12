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
import fileUtils._


object fileIO {

  import params.StorageParams.toplevelPath

  implicit val dtfmt = DateTimeFormat.forPattern("dd.MM.yyyy, HH:mm:ss")
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)

  def getTimeStringUnsafe: String = DateTime.now().toString(tfmt)

  // TODO might be perf loss to go from Array to List and all that
  def readCSV[F[_]: Concurrent : ContextShift](filePath: Path): Stream[F,Int] = {
    val reader = fs2.io.file.readAll[F](filePath, backendImplicits.ec, 4096*32)
      .through(text.utf8Decode)
      .through(text.lines)
      .map{ csvLine => Chunk.seq(csvLine.split(",").map(_.toFloat.toInt))} // tofloat toint???
      .through(utilz.chunkify)
      .handleErrorWith{
        case e: java.lang.NumberFormatException => { say("Record done"); Stream.empty}
        case e: Exception => { say(s"very bad error ${e.printStackTrace()}"); Stream.empty }
      }

    reader
  }

  def readGZIP[F[_]: Effect](filePath: Path): Stream[F,Int] = ???


  def getTimestampedFilename[F[_]: Sync]: F[Path] = {
    import params.StorageParams.toplevelPath
    getDateTimeString.map(s => Paths.get(toplevelPath + s))
  }


  /**
    Creates a new file and returns the path, plus a sink for writing to said file
    */
  def writeCSV[F[_]: Concurrent : ContextShift]: F[(Path, Sink[F,Int])] = {

    import params.StorageParams.toplevelPath

    getDateTimeString map { s =>
      val path: Path = Paths.get(toplevelPath + s)
      val sink: Sink[F,Int] = _
        .chunkN(1000, true)
        .map(_.toList.mkString("",", ", "\n"))
        .through(text.utf8Encode)
        .through(fs2.io.file.writeAll(path, backendImplicits.ec))

      (path, sink)
    }
  }


  /**
    Writes events to a file
    */
  def timestampedEventWriter[F[_]: Concurrent : ContextShift](implicit ev: Timer[F]): Path => Sink[F,String] = { path =>
    val eventPath = Paths.get(path.toString() + "_events")
    s => s
      .through(timeStamp)
      .through(text.utf8Encode)
      .through(fs2.io.file.writeAll(eventPath, backendImplicits.ec))
  }

  /**
    Attaches an eventWriter to a stream
    */
  def attachEventWriter[F[_]: Concurrent : Timer, O](writer: Sink[F,String], serialize: O => String)(implicit ec: EC): Pipe[F,O,O] = {
    val writeSink: Sink[F,O] = s => s.through(_.map(serialize)).through(writer)
    s => s.observeAsync(100)(writeSink)
  }


  /**
    * Outputs a sink and path for writing an integer stream as a
    * compressed file. Use default values for deflate.
    */
  def writeCompressed[F[_]: Concurrent : ContextShift]: F[(Path, Sink[F,Int])] = {
    import params.StorageParams.toplevelPath

    getDateTimeString map { s =>
      val path: Path = Paths.get(toplevelPath + s)
      val sink: Sink[F, Int] = _
        .through(utilz.intToBytes)
        .through(fs2.compress.deflate())
        .through(fs2.io.file.writeAll(path, backendImplicits.ec))

      (path, sink)
    }
  }


  /**
    * Reads a compressed binary file of integers. Use default values
    * for inflate.
    */
  def streamCompressed[F[_]: Concurrent : ContextShift](filePath: String): Stream[F,Int] = {
    import params.StorageParams.toplevelPath

    fs2.io.file.readAll(Paths.get(toplevelPath + filePath), backendImplicits.ec, 4096)
      .through(fs2.compress.inflate())
      .through(bytesToIntsJVM)
  }


  /**
    Streams data from a file representing a single channel
    */
  def streamChannelData[F[_]: Concurrent : ContextShift](filePath: String): Stream[F,Int] = {
    fs2.io.file.readAll(Paths.get(filePath), backendImplicits.ec, 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .filter(!_.isEmpty)
      .map(x => Chunk.seq(x.split(",").map(_.toInt)))
      .through(utilz.chunkify)
  }

  def stringToFile[F[_]: Concurrent : ContextShift](s: String, path: Path): Stream[F,Unit] = {
    Stream.emit(s).covary[F].through(text.utf8Encode).through(fs2.io.file.writeAll(path, backendImplicits.ec))
  }
}
