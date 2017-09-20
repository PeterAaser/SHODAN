package com.cyborg

import fs2._
import fs2.Stream._
import fs2.async.mutable.{ Queue, Topic }
import fs2.io.tcp._

import cats.effect.IO

import wallAvoid.Agent
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import utilz._

import scala.language.higherKinds


object sIO {

  import backendImplicits._

  type ChannelStream[F[_],A]     = Stream[F,Vector[Stream[F,A]]]

  // TODO these should be Option
  // then every function can take them as args and check if they exist
  type FullStreamHandler[F[_]]   = Sink[F,Byte]
  type SelectStreamHandler[F[_]] = Sink[F,Byte]
  type FeedbackStream[F[_]]      = Stream[F,Byte]

  // TODO remove
  // type dataSegment = (Vector[Int], Int)
  // type dataTopic[F[_]] = Topic[F,dataSegment]
  // type meameDataTopic[F[_]] = List[Topic[F,dataSegment]]
  // type dbDataTopic[F[_]] = List[Topic[F,dataSegment]]
  // type Channel = Int

  import params.experiment._


  /**
    * For offline playback of data, select experiment id to publish on provided topics
    */
  def streamFromDatabase(experimentId: Int, topics: dbDataTopic[IO]): Stream[IO, Unit] = {
    for {
      experimentInfo <- databaseIO.dbReaders.getExperimentSampleRate(experimentId) // samplerate, not use atm
      experimentData = databaseIO.dbReaders.dbChannelStream(experimentId)
    } yield {
      Assemblers.broadcastDataStream(experimentData, topics)
    }
  }

  /**
    * Stream data to a database from a list of topics yada yada
    */
  def streamToDatabase(topics: dbDataTopic[IO], comment: String): Stream[IO, Unit] = {
    for {
      id <- eval(databaseIO.dbWriters.createExperiment(Some(comment)))
      _ <- databaseIO.dbWriters.startRecording(topics, id)
    } yield ()
  }


  /**
    * Open a TCP connection to stream data from other computer
    * Data is broadcasted to provided topics
    */
  def streamFromTCP(topics: meameDataTopic[Task]): Stream[IO, Unit] = {
    val experimentData = networkIO.streamAllChannels[IO]
    Assemblers.broadcastDataStream(experimentData, topics)
  }


  /**
    * Open a TCP connection to stream stimuli requests to MEAME
    */
  def stimulusToTCP(stimuli: Stream[IO,String]): Stream[IO, Unit] = ???

}
