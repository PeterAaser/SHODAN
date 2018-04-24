package cyborg

import org.joda.time._
import org.joda.time.Interval
import org.joda.time.convert._
import org.joda.convert._
import org.joda.time.format.DateTimeFormat
import utilz._

import cats.implicits._
import cats.effect._

import fs2._
import fs2.Stream._
import cats.effect.IO
import java.nio.file.Path

import cyborg.utilz._
import cyborg.doobIO._
import cyborg.RPCmessages._

import cats.effect.IO


object databaseIO {

  import backendImplicits._


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
    Gets a resource URI from the database and returns the content of the file as a Stream
    */
  def dbChannelStream(experimentId: Int)(implicit ec: EC): Stream[IO, Int] = {
    say(s"making stream for experiment $experimentId")

    val data = Stream.eval(doobIO.getExperimentDataURI(experimentId)).transact(xa) flatMap { (data: DataRecording) =>
      Stream.eval(doobIO.getExperimentParams(experimentId)).transact(xa) flatMap { expParams =>
        say(s"Playing record with parameters:")
        say(s"id:             ${expParams.id}")
        say(s"samplerate:     ${expParams.samplerate}")
        say(s"segment length: ${expParams.segmentLength}")
        val reader = data.resourceType match {
          case CSV => fileIO.readCSV[IO](data.resourcePath, expParams.samplerate)
          case GZIP => fileIO.readGZIP[IO](data.resourcePath)
        }
        reader
      }
    }

    data
  }

  def newestRecordingId(implicit ec: EC): IO[Int] =
    getNewestExperimentId.transact(xa)

  def newestRecording(implicit ec: EC): Stream[IO, Int] = {
    say(s"making stream for newest experiment")

    val newest = getNewestExperimentId.transact(xa)

    Stream.eval(newest) flatMap{ experimentId =>
      say(s"playing experiment with id $experimentId")
      val data = Stream.eval(doobIO.getExperimentDataURI(experimentId)).transact(xa) flatMap { (data: DataRecording) =>
        Stream.eval(doobIO.getExperimentParams(experimentId)).transact(xa) flatMap { expParams =>
          say(s"Playing record with parameters:")
          say(s"id:             ${expParams.id}")
          say(s"samplerate:     ${expParams.samplerate}")
          say(s"segment length: ${expParams.segmentLength}")
          val reader = data.resourceType match {
            case CSV => fileIO.readCSV[IO](data.resourcePath, expParams.samplerate)
            case GZIP => fileIO.readGZIP[IO](data.resourcePath)
          }
          reader
        }
      }
      data
    }
  }


  def getRecordingInfo(id: Int)(implicit ec: EC): IO[RecordingInfo] = {

    val info = doobIO.getExperimentInfo(id)
    val params = doobIO.getExperimentParams(id)

    // hideous
    def shittyFormat(s: Seconds): String = {
      val midnight = new LocalTime(0, 0)
      val added = midnight.minus(s)
      DateTimeFormat.forPattern("HH:mm:ss").print(added)
    }

    info.flatMap { info =>
      params.map { params =>
        val setting = Setting.ExperimentSettings(params.samplerate,Nil,params.segmentLength)
        val timeString = info.startTime.toString()
        val duration = info.finishTime.map{ f => shittyFormat(Seconds.secondsBetween(f, info.startTime)) }

        RecordingInfo(setting,
                      id.toInt,
                      timeString,
                      duration,
                      None,
                      info.comment)
      }
    }.transact(xa)
  }

  def getAllExperiments(implicit ec: EC): IO[List[RecordingInfo]] = {
    getAllExperimentIds.flatMap { ids =>
      ids.map(getRecordingInfo).sequence
    }
  }

  def dbGetParams(experimentId: Int): IO[Int] = {
    val params = doobIO.getExperimentParams(experimentId)
      .transact(xa)
      .map(_.segmentLength)

    params
  }


  /**
    Sets up the database stuff and returns a sink for recorded data
    The sink only gets created at the first received element, thus
    ensuring that a database recording will only be made once data
    actually arrives.
    */
  case class RecordingSink(finalizer: IO[Unit], sink: Sink[IO,Int])
  def createRecordingSink(comment: String, getConf: IO[Setting.FullSettings])(implicit ec: EC): IO[RecordingSink] = {

    import fs2.async._
    import cats.effect.IO
    import cats.effect._
    import cats.implicits._

    // Action for setting up path, sink and experiment record.
    // Since experiment record wont be inserted before 1st element
    // we do not need to keep track of the time in the program.
    // We also supply a finalizer method that will NYI freeze everything
    signalOf[IO,IO[Unit]](IO.unit) map { finalizer =>

      val onFirstElement = for {
        pathAndSink  <- fileIO.writeCSV[IO]
        conf         <- getConf
        experimentId <- insertNewExperiment(pathAndSink._1, comment,conf.experimentSettings).transact(xa)
        _ = say("on first element for comp running")
        _            <- finalizer.set(finalizeExperiment(experimentId).transact(xa).void)
      } yield (pathAndSink._2)

      RecordingSink(finalizer.get.flatten, createOnFirstElement(onFirstElement, identity[Sink[IO,Int]]))
    }
  }


  def getAllExperimentIds(): IO[List[Int]] =
    doobIO.getAllExperiments.transact(xa)

  def getAllExperimentUris(): IO[List[Path]] =
    doobIO.getAllExperimentUris.transact(xa)

  def insertOldExperiment(comment: String, timestamp: DateTime, uri: String): IO[Unit] =
    doobIO.insertOldExperiment(comment, timestamp, uri).transact(xa)


  def filterByTimeInterval(interval: Interval): List[ExperimentInfo] => List[ExperimentInfo] = {
    records => records
      .filter(_.finishTime.isDefined)
      .filter{ z => interval.contains(new Interval(z.startTime, z.finishTime.get)) }
  }
}
