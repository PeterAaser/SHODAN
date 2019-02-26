package cyborg.io.database
import cats.Monad
import cats.data.Kleisli
import cats.effect.implicits._
import cats.implicits._
import cats.effect._

import cyborg._
import cyborg.io.files._
import cyborg.Settings._

import org.joda.time._
import org.joda.time.Interval
import org.joda.time.convert._
import org.joda.convert._
import org.joda.time.format.DateTimeFormat
import utilz._

import fs2._
import fs2.Stream._
import fs2.concurrent.SignallingRef
import java.nio.file.Path

import cyborg.utilz._
import DoobieQueries._
import cyborg.RPCmessages._

import cats.effect.IO
import backendImplicits._

object databaseIO {


  implicit val shouldThisReallyBeHere = IO.contextShift _

  // haha nice meme dude!
  val username = "memer"
  val superdupersecretPassword = "memes"

  import doobie._
  import doobie.implicits._
  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:memestorage",
    s"$username",
    s"$superdupersecretPassword"
  )


  /**
    Gets a resource URI from the database and returns the content of the file as a Stream
    */
  def dbChannelStream(experimentId: Int): Stream[IO, Int] = {
    say(s"making stream for experiment $experimentId")

    val data = Stream.eval(DoobieQueries.getExperimentDataURI(experimentId)).transact(xa) flatMap { (data: DataRecording) =>
      Stream.eval(DoobieQueries.getExperimentParams(experimentId)).transact(xa) flatMap { expParams =>
        val reader = data.resourceType match {
          case CSV => fileIO.readCSV[IO](data.resourcePath)
          case GZIP => fileIO.readGZIP[IO](data.resourcePath)
        }
        reader
      }
    }

    say("DOWNSCALING DATABASE OUTPUT", Console.RED)

    data.map(x => (x.toDouble * 5.9605e-5).toInt)
  }

  def newestRecordingId: IO[Int] = getNewestExperimentId.transact(xa)

  def newestRecording: Stream[IO, Int] = {

    say(s"making stream for newest experiment")

    val newest = getNewestExperimentId.transact(xa)

    Stream.eval(newest) flatMap{ experimentId =>
      say(s"playing experiment with id $experimentId")
      val data = Stream.eval(DoobieQueries.getExperimentDataURI(experimentId)).transact(xa) flatMap { (data: DataRecording) =>
        Stream.eval(DoobieQueries.getExperimentParams(experimentId)).transact(xa) flatMap { expParams =>
          val reader = data.resourceType match {
            case CSV => fileIO.readCSV[IO](data.resourcePath)
            case GZIP => fileIO.readGZIP[IO](data.resourcePath)
          }
          reader
        }
      }
      data
    }
  }


  /**
    TODO kinda not a conf reader
    */
  def streamToDatabase(rawDataStream: Stream[IO,TaggedSegment], comment: String)
    : Kleisli[IO,FullSettings,InterruptableAction[IO]] = {

    def runStream(recordingSink: RecordingSink) = {
      SignallingRef[IO,Boolean](false).map { interruptSignal =>
        InterruptableAction(
          interruptSignal.set(true) >> recordingSink.finalizer,
          rawDataStream
            .dropWhile(seg => seg.channel != 0)
            .through(_.map(_.data))
            .through(chunkify)
            .through(recordingSink.sink)
            .interruptWhen(interruptSignal.discrete).compile.drain
        )
      }
    }

    for {
      sink   <- createRecordingSink(comment)
      action <- Kleisli.liftF(runStream(sink))
    } yield (action)
  }


  def getRecordingInfo(id: Int): IO[RecordingInfo] = {

    val info = DoobieQueries.getExperimentInfo(id)
    val params = DoobieQueries.getExperimentParams(id)

    // hideous
    def shittyFormat(s: Seconds): String = {
      val midnight = new LocalTime(0, 0)
      val added = midnight.minus(s)
      DateTimeFormat.forPattern("HH:mm:ss").print(added)
    }

    info.flatMap { info =>
      params.map { params =>
        val setting = Settings.DAQSettings(params.samplerate, params.segmentLength)
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

  def dbGetParams(experimentId: Int): IO[ExperimentParams] = {
    val params = DoobieQueries.getExperimentParams(experimentId)
      .transact(xa)

    params
  }


  /**
    Sets up the database stuff and returns a sink for recorded data
    The sink only gets created at the first received element, thus
    ensuring that a database recording will only be made once data
    actually arrives.

    TODO: This might have the risk of stale conf.
    */
  /**
    TODO: Uhh, what does this actually do??
    */
  case class RecordingSink(finalizer: IO[Unit], sink: Sink[IO,Int])
  def createRecordingSink(comment: String) = Kleisli[IO, FullSettings, RecordingSink]{ conf =>
    SignallingRef[IO,IO[Unit]](IO.unit) map { finalizer =>

      val onFirstElement = for {
        _            <- Fsay[IO]("First element received, creating DB record")
        pathAndSink  <- fileIO.writeCSV[IO]
        experimentId <- insertNewExperiment(pathAndSink._1, comment, conf.daq).transact(xa)
        _            <- finalizer.set(finalizeExperiment(experimentId).transact(xa).void)
      } yield (pathAndSink._2)

      RecordingSink(finalizer.get.flatten, createOnFirstElement(onFirstElement, identity[Sink[IO,Int]]))
    }
  }


  def getAllExperimentIds(): IO[List[Int]] =
    DoobieQueries.getAllExperiments.transact(xa)

  def getAllExperimentUris(): IO[List[Path]] =
    DoobieQueries.getAllExperimentUris.transact(xa)

  def insertOldExperiment(comment: String, timestamp: DateTime, uri: String): IO[Unit] =
    DoobieQueries.insertOldExperiment(comment, timestamp, uri).transact(xa)


  def filterByTimeInterval(interval: Interval): List[ExperimentInfo] => List[ExperimentInfo] = {
    records => records
      .filter(_.finishTime.isDefined)
      .filter{ z => interval.contains(new Interval(z.startTime, z.finishTime.get)) }
  }
}
