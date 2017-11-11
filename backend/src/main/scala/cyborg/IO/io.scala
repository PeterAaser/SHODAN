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
      experimentData.through(tagPipe(p))
    )
  }


  /**
    * Stream data to a database from a list of topics yada yada
    */
  def streamToDatabase(rawDataStream: Stream[IO,TaggedSegment], comment: String): Stream[IO, Unit] =
    Stream.eval(databaseIO.createRecordingSink(comment)) flatMap ( sink =>
      rawDataStream.through(_.map(_.data._2)).through(chunkify).through(sink))


  /**
    * Open a TCP connection to stream data from other computer
    * Data is broadcasted to provided topics
    */
  def streamFromTCP(segmentLength: Int)(implicit ec: ExecutionContext): Stream[IO,TaggedSegment] =
    networkIO.streamAllChannels[IO].through(tagPipe(segmentLength))
}
