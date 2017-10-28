package cyborg

import cats.effect.IO
import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import java.io.File
import java.nio.file.Path
import scala.concurrent.ExecutionContext

import fs2._


/**
  Takes in a filepath, performs the magic.
  */
object mcsParser {

  case class recordingInfo(comment: String, MEA: Int, timestamp: DateTime){
    def makeString = Some("Info string generated from recordingInfo")
  }


  /**
    Attempts to parse information in the metadata which is collected
    from the folder paths etc by the mcs_unfucker.py job
    */
  def getInfo(infoPath: Path): Stream[IO,recordingInfo] = {

    def parseFolderDate(folderPath: Path): DateTime = {
      val pyFormat = DateTimeFormat.forPattern("YYYY-MM-DD_HH-mm-ss")
      DateTime.parse(folderPath.getFileName.toString().dropRight(4), pyFormat)
    }

    val s: Stream[IO,String] = fs2.io.file.readAll[IO](infoPath, 4096)
      .through(text.utf8Decode)
      .through(text.lines)

    def theThing: Pipe[IO,String,recordingInfo] = {
      def go(s: Stream[IO,String]): Pull[IO,recordingInfo,Unit] = {
        s.pull.unconsN(10, true).flatMap {
          case Some((strangz, tl)) => {
            var MEA: Int = -1
            var comment = ""
            val info = strangz.toVector
            if(info(0) contains "MEA"){
              MEA = info(0).split(":")(1).toInt
              comment = info(1)
            }
            else{
              MEA = -1
              comment = info(0)
            }

            val timestamp = parseFolderDate(infoPath)
            println(Console.RED + "UNIMPLEMENTED METHOD. DATE IS WRONG")
            Pull.output1(recordingInfo(comment, MEA, timestamp))
          }
          case None => {
            println("WEEEE WOOO WEEEE WOOO SOMETHING IS WRONG!!")
            Pull.done
          }
        }
      }
      in => go(in).stream
    }
    s.through(theThing)
  }


  /**
    Adds a record to the database
    */
  def processRecordings(implicit ec: ExecutionContext): Stream[IO,Unit] = {

    // Query for all URIs currently in the database
    val dbUris = databaseIO.getAllExperimentUris.map(_.toSet)

    // Get list of recordings in the data folder
    val fileUris: Set[Path] = (
      fileIO.getListOfFiles("/home/peteraa/MEAdata") ++
      fileIO.getListOfFiles("/home/peteraa/MEAdata/mcs_data")
    ).map(_.toPath()).toSet


    // Find all uris not inserted and pair them with their metadata
    val uninserted = dbUris.map( dbUris => fileUris -- dbUris).map{ uris =>
      uris.map { uri =>

        val metaData = fileIO.getListOfFiles("/home/peteraa/MEAdata/mcs_data/metadata")
          .filterNot(λ => λ.getName.eq(uri.getFileName))
          .head.toPath() // #YOLO

        (uri, metaData)
      }.toList
    }


    // For the records not added, create metadata in database
    Stream.eval(uninserted) flatMap { uris =>
      val insertions = uris.map {
        case(uri, metadata) =>
          getInfo(metadata) flatMap { info =>
            Stream.eval(databaseIO.insertOldExperiment(info.comment, info.timestamp, uri.toString()))
          }
      }
      Stream.emits(insertions).joinUnbounded
    }
  }
}
