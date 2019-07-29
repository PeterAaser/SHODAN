package cyborg.io
import cats.data.Kleisli
import cats.data.StateT
import cats.effect._
import cats._
import cyborg._
import cyborg.io.files._
import cyborg.io.network._
import cyborg.RPCmessages._

import cyborg.Settings._

import fs2._
import fs2.Stream._

import cats.effect.IO
import cats.implicits._
import fs2.concurrent.Topic
import scala.concurrent.duration._

import cyborg.io.database._
import utilz._
import backendImplicits._

import cyborg.backend.Launcher.ioTimer

object DB {
  import cyborg.io.database._

  /**
    * For offline playback of data, select experiment id to publish on provided topics
    */
  def streamFromDatabase(experimentId: Int): Stream[IO, TaggedSegment] = {
    say("streaming..")
    val experimentData = databaseIO.dbChannelStream(experimentId)
    val params = databaseIO.dbGetParams(experimentId)
    Stream.eval(params).flatMap ( p =>
      experimentData
        .through(tagPipe(p.segmentLength)))
  }


  def streamFromDatabaseThrottled(experimentId: Int): Stream[IO, TaggedSegment] = {
    say("streaming throttled")
    val experimentData = databaseIO.dbChannelStream(experimentId)
    val params = databaseIO.dbGetParams(experimentId)
    say(params.unsafeRunSync())
    Stream.eval(params).flatMap ( p =>
      experimentData
        .through(utilz.throttlerPipe[IO,Int](p.samplerate*60, 0.05.second))
        .through(tagPipe(p.segmentLength)))
  }


  def streamFromDatabaseST(experimentId: Int) = StateT[IO,FullSettings,Stream[IO,TaggedSegment]]{ conf =>
    databaseIO.dbGetParams(experimentId).map { storedConf =>
      val nextConf = conf.copy(daq = conf.daq.copy(samplerate = storedConf.samplerate, segmentLength = storedConf.segmentLength))
      (nextConf,
       databaseIO.dbChannelStream(experimentId)
        .through(utilz.throttlerPipe[IO,Int](nextConf.daq.samplerate*60, 0.05.second))
        .through(tagPipe(nextConf.daq.segmentLength)))
    }
  }

  def getAllExperiments: IO[List[RecordingInfo]] = {
    databaseIO.getAllExperimentIds.flatMap { ids =>
      ids.map(databaseIO.getRecordingInfo).sequence
    }
  }


  /**
    * Writes data to a CSV file. The metadata is stored to database
    */
  def streamToDatabase(rawDataStream: Stream[IO,Int],
                       comment: String): Kleisli[IO,FullSettings,InterruptableAction[IO]] =
    databaseIO.streamToDatabase(rawDataStream, comment)
}


object File {

  /**
    * For when we don't really need to log the metadata and just want to store to file
    */
  def streamToFile(rawDataStream: Stream[IO,TaggedSegment]): Stream[IO, Unit] = {
    Stream.eval(fileIO.writeCSV[IO]) flatMap { x =>
      rawDataStream.through(_.map(_.data)).through(chunkify).through(x._2)
    }
  }

}

object Network {

  /**
    * Open a TCP connection to stream data from other computer
    * Data is broadcasted to provided topics
    */
  def streamFromTCP: Kleisli[Id,FullSettings,Stream[IO,TaggedSegment]] = for {
    conf <- Kleisli.ask[Id,FullSettings]
    s    <- networkIO.streamAllChannels[IO]
  } yield s.through(tagPipe(conf.daq.segmentLength))
}
