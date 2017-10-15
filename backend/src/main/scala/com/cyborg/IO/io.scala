package com.cyborg

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
  def streamFromDatabase(experimentId: Int, topics: List[Topic[IO,DataSegment]])(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    // for {
    //   experimentInfo <- databaseIO.dbReaders.getExperimentSampleRate(experimentId) // samplerate, not use atm
    // } yield {
    val experimentData = databaseIO.dbReaders.dbChannelStream(experimentId)
    Assemblers.broadcastDataStream(experimentData, topics)
  }

  /**
    * Stream data to a database from a list of topics yada yada
    */
  def streamToDatabase(topics: MeameDataTopic[IO], comment: String)(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    for {
      id <- eval(databaseIO.dbWriters.createExperiment(Some(comment)))
      _ <- databaseIO.dbWriters.startRecording(topics, id)
    } yield ()
  }


  /**
    * Open a TCP connection to stream data from other computer
    * Data is broadcasted to provided topics
    */
  def streamFromTCP(topics: MeameDataTopic[IO])(implicit ec: ExecutionContext): Stream[IO, Unit] = {
    val experimentData = networkIO.streamAllChannels[IO]
    Assemblers.broadcastDataStream(experimentData, topics)
  }


  /**
    * Open a TCP connection to stream stimuli requests to MEAME
    */
  def stimulusToTCP(stimuli: Stream[IO,String]): Stream[IO, Unit] = ???


}

object dIO {
  def streamFromDatabaseRaw(experimentId: Int)(implicit ec: ExecutionContext): Stream[IO, Int] = {
    val experimentData = databaseIO.dbReaders.dbChannelStream(experimentId)
    experimentData
  }
}
