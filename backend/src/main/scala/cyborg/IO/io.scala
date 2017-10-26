package cyborg

import fs2._
import fs2.Stream._

import cats.effect.IO
import fs2.async.mutable.Topic
import scala.concurrent.ExecutionContext

import utilz._

import scala.language.higherKinds


object sIO {

  type ChannelStream[F[_],A]     = Stream[F,Vector[Stream[F,A]]]

  // TODO these should be Option
  // then every function can take them as args and check if they exist
  type FullStreamHandler[F[_]]   = Sink[F,Byte]
  type SelectStreamHandler[F[_]] = Sink[F,Byte]
  type FeedbackStream[F[_]]      = Stream[F,Byte]



  /**
    * For offline playback of data, select experiment id to publish on provided topics
    */
  def streamFromDatabase(experimentId: Int, topics: List[Topic[IO,DataSegment]], rawDataSink: Sink[IO,Int])(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    val (experimentParams, experimentData) = databaseIO.dbChannelStream(experimentId)
    Stream.eval(experimentParams) flatMap ( segmentLength =>
      Assemblers.broadcastDataStream(experimentData, topics, rawDataSink, segmentLength))
  }



  /**
    * Stream data to a database from a list of topics yada yada
    */
  def streamToDatabase(rawDataStream: Stream[IO,Int], comment: String): Stream[IO, Unit] =
    Stream.eval(databaseIO.createRecordingSink(comment)) flatMap ( sink =>
      rawDataStream.through(sink))



  /**
    * Open a TCP connection to stream data from other computer
    * Data is broadcasted to provided topics
    */
  def streamFromTCP(topics: MeameDataTopic[IO], rawDataSink: Sink[IO, Int])(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    val experimentData = networkIO.streamAllChannels[IO]
    Assemblers.broadcastDataStream(experimentData, topics, rawDataSink, params.experiment.segmentLength)
  }


  /**
    * Open a TCP connection to stream stimuli requests to MEAME
    */
  // TODO: Should be done with http POST, not as a TCP stream
  def stimulusToTCP(stimuli: Stream[IO,String]): Stream[IO, Unit] = ???

}


/**
  TODO Move this to a real testing backend, fucks sake...
  */
object dIO {

  // Creates different sine waves for each channel.
  def channelIdStream: Stream[IO,Int] = {
    utilz.sineWave[IO](60, params.experiment.segmentLength)
  }
}
