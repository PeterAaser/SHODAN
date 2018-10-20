package cyborg.io
import cats.effect.Effect
import cyborg._
import cyborg.io.files._
import cyborg.io.network._
import cyborg.RPCmessages._


import fs2._
import fs2.Stream._

import cats.effect.IO
import cats.implicits._
import fs2.async.mutable.Topic
import scala.concurrent.ExecutionContext

import cyborg.io.database._
import utilz._

object sIO {

  object DB {
    import cyborg.io.database._

    /**
      * For offline playback of data, select experiment id to publish on provided topics
      */
    def streamFromDatabase(experimentId: Int)(implicit ec: ExecutionContext): Stream[IO, TaggedSegment] = {
      val experimentData = databaseIO.dbChannelStream(experimentId)
      val params = databaseIO.dbGetParams(experimentId)

      Stream.eval(params).flatMap ( p =>
        experimentData.through(tagPipe(p))
      )
    }


    def getAllExperiments(implicit ec: EC): IO[List[RecordingInfo]] = {
      databaseIO.getAllExperimentIds.flatMap { ids =>
        ids.map(databaseIO.getRecordingInfo).sequence
      }
    }


    /**
      * Writes data to a CSV file. The metadata is stored to database
      */
    def streamToDatabase(
      rawDataStream: Stream[IO,TaggedSegment],
      comment: String,
      getConf: IO[Setting.FullSettings])
      (implicit ec: EC): IO[InterruptableAction[IO]] = databaseIO.streamToDatabase(rawDataStream, comment, getConf)
  }


  object File {

    /**
      * For when we don't really need to log the metadata and just want to store to file
      */
    def streamToFile(rawDataStream: Stream[IO,TaggedSegment])(implicit ec: EC): Stream[IO, Unit] = {
      Stream.eval(fileIO.writeCSV[IO]) flatMap { λ =>
        rawDataStream.through(_.map(_.data)).through(chunkify).through(λ._2)
      }
    }

  }

  object Network {

    /**
      * Open a TCP connection to stream data from other computer
      * Data is broadcasted to provided topics
      */
    def streamFromTCP(segmentLength: Int)(implicit ec: ExecutionContext): Stream[IO,TaggedSegment] =
      networkIO.streamAllChannels[IO].through(tagPipe(segmentLength))

    /**
      * Opens a TCP server which exposes the channels individually.
      * Once SHODAN is running, another consumer may request a datastream by sending a byte to the
      * server and reading the returned data.
      *
      * Very crufty, crashes the server upon consumer disconnecting lol
      */
    def channelServer[F[_]: Effect](topics: List[Topic[F,TaggedSegment]])(implicit ec: EC): Stream[F,F[Unit]] =
      networkIO.channelServer(topics)
  }
}
