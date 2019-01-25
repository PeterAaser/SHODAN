package cyborg.io
import cats.data.Kleisli
import cats.effect._
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

object sIO {

  object DB {
    import cyborg.io.database._

    /**
      * For offline playback of data, select experiment id to publish on provided topics
      */
    def streamFromDatabase(experimentId: Int): Stream[IO, TaggedSegment] = {
      val experimentData = databaseIO.dbChannelStream(experimentId)
      val params = databaseIO.dbGetParams(experimentId)
      Stream.eval(params).flatMap ( p =>
        experimentData
          .through(tagPipe(p.segmentLength)))
    }


    def streamFromDatabaseThrottled(experimentId: Int): Stream[IO, TaggedSegment] = {
      val experimentData = databaseIO.dbChannelStream(experimentId)
      val params = databaseIO.dbGetParams(experimentId)
      say(params.unsafeRunSync())
      Stream.eval(params).flatMap ( p =>
        experimentData
          .through(utilz.throttlerPipe[IO,Int](p.samplerate*60, 0.05.second))
          .through(tagPipe(p.segmentLength)))
    }


    def getAllExperiments: IO[List[RecordingInfo]] = {
      databaseIO.getAllExperimentIds.flatMap { ids =>
        ids.map(databaseIO.getRecordingInfo).sequence
      }
    }


    /**
      * Writes data to a CSV file. The metadata is stored to database
      */
    def streamToDatabase(rawDataStream: Stream[IO,TaggedSegment],
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
    def streamFromTCP(segmentLength: Int): Stream[IO,TaggedSegment] =
      networkIO.streamAllChannels[IO].through(tagPipe(segmentLength))

    /**
      * Opens a TCP server which exposes the channels individually.
      * Once SHODAN is running, another consumer may request a datastream by sending a byte to the
      * server and reading the returned data.
      *
      * Very crufty, crashes the server upon consumer disconnecting lol
      */
    def channelServer[F[_]: ConcurrentEffect : ContextShift](topics: List[Topic[F,TaggedSegment]]): Stream[F,F[Unit]] =
      networkIO.channelServer(topics)
  }
}
