package com.cyborg

import fs2._
import fs2.Stream._
import fs2.async.mutable.Queue
import fs2.util.Async
import fs2.io.tcp._

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

  import params.experiment._


  /**
    * For offline playback of data, select experiment id to publish on provided topics
    */
  def streamFromDatabase(experimentId: Int, topics: dbDataTopic[Task]): Stream[Task, Unit] = {
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
  def streamToDatabase(topics: dbDataTopic[Task], comment: String): Stream[Task, Unit] = {
    for {
      id <- eval(databaseIO.dbWriters.createExperiment(Some(comment)))
      _ <- databaseIO.dbWriters.startRecording(topics, id)
    } yield ()
  }


  /**
    * Open a TCP connection to stream data from other computer
    * Data is broadcasted to provided topics
    */
  def streamFromTCP(topics: meameDataTopic[Task]): Stream[Task, Unit] = {
    val experimentData = networkIO.streamAllChannels[Task]
    Assemblers.broadcastDataStream(experimentData, topics)
  }


  /**
    * Open a TCP connection to stream stimuli requests to MEAME
    */
  def stimulusToTCP(stimuli: Stream[Task,String]): Stream[Task, Unit] = ???

}
