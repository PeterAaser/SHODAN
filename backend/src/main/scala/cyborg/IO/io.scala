package cyborg

import fs2._
import fs2.Stream._

import cats.effect.IO
import scala.concurrent.ExecutionContext

import utilz._

object sIO {
  /**
    * For offline playback of data, select experiment id to publish on provided topics
    */
  def streamFromDatabase(experimentId: Int)(implicit ec: ExecutionContext): Stream[IO, TaggedSegment] = {
    val experimentData = databaseIO.dbChannelStream(experimentId)
    val params = databaseIO.dbGetParams(experimentId)

    Stream.eval(params).flatMap ( p =>
      // experimentData.through(testTagPipe(p))
      experimentData.through(tagPipe(p))
    )
  }


  /**
    * Writes data to a CSV file. The metadata is stored to database
    */
  def streamToDatabase(
    rawDataStream: Stream[IO,TaggedSegment],
    comment: String,
    getConf: IO[Setting.FullSettings])
    (implicit ec: EC): IO[InterruptableAction[IO]] =
  {
    import fs2.async._
    import cats.implicits._

    signalOf[IO,Boolean](false).flatMap { interruptSignal =>
      databaseIO.createRecordingSink("", getConf).map { recordingSink =>
        InterruptableAction(
          interruptSignal.set(true) >> recordingSink.finalizer,
          rawDataStream
            .through(_.map(_.data))
            .through(chunkify)
            .through(recordingSink.sink)
            .interruptWhen(interruptSignal.discrete).run
        )
      }
    }
  }


  /**
    * For when we don't really need to log the metadata and just want to store to file
    */
  def streamToFile(rawDataStream: Stream[IO,TaggedSegment])(implicit ec: EC): Stream[IO, Unit] = {
    Stream.eval(fileIO.writeCSV[IO]) flatMap { λ =>
      rawDataStream.through(_.map(_.data)).through(chunkify).through(λ._2)
    }
  }


  /**
    * Open a TCP connection to stream data from other computer
    * Data is broadcasted to provided topics
    */
  def streamFromTCP(segmentLength: Int)(implicit ec: ExecutionContext): Stream[IO,TaggedSegment] =
    // networkIO.streamAllChannels[IO].through(testTagPipe(segmentLength))
    networkIO.streamAllChannels[IO].through(tagPipe(segmentLength))
}
