package com.cyborg

import fs2._
import fs2.Stream._
import utilz._

import scala.language.higherKinds

object databaseIO {

  type ExperimentId = Long

  object dbReaders {

    /**
      Breaks bigass database chunks into something more manageable
      */
    def arrayBreaker[F[_]](chunkSize: Int): Pipe[F,Array[Int], Int] = {
      def get: Handle[F,Array[Int]] => Pull[F,Int,Unit] = h => {
        h.receive1 {
          (v, h) => {
            val grouped = v.grouped(chunkSize).toList
            unload(grouped)(h)
          }
        }
      }
      def unload(dudes: List[Array[Int]]): Handle[F,Array[Int]] => Pull[F,Int,Unit] = h => {
        dudes match {
          case head :: t => Pull.output(Chunk.seq(head)) >> unload(t)(h)
          case _ => get(h)
        }
      }
      _.pull(get)
    }


    /**
      Reads from the database, returning a stream of integers as if they were collected live.
      This means we have to demux them all over.
      */
    def dbChannelStream(experimentId: Int): Stream[Task, Int] = {

      // This is OK because we only multiplex, thus segmentLength when recorded is irrelevant.
      import params.experiment.segmentLength

      // A list of streams from each channel
      val dbChannelStreams: Stream[Task, List[Stream[Task, Array[Int]]]] =
        Stream.eval(doobieTasks.doobieReaders.selectChannelStreams(experimentId, (0 to 60).toList))

      // An unpacking function breaking down huge pieces of database data
      def unpack = (s: Stream[Task,Array[Int]]) =>
      s.through(arrayBreaker(segmentLength)).through(utilz.vectorize(segmentLength))

      dbChannelStreams
        .map(_.map(_.through(unpack)))
        .through(utilz.roundRobin)
        .through(utilz.chunkify)
        .through(utilz.chunkify)
    }

    def getExperimentSampleRate(experimentId: Int): Stream[Task,Int] =
      doobieTasks.doobieReaders.getExperimentInfo(experimentId).map(_.sampleRate)
  }

  object dbWriters {

    /**
      Creates a new experiment and sets up channels etc
      */
    def createExperiment(comment: Option[String]): Task[ExperimentId] = {
      for {
        id <- doobieTasks.doobieWriters.insertNewExperiment(comment)
        _ <- doobieTasks.doobieWriters.insertChannels(id)
      } yield (id)
    }


    /**
      Creates a sink that inserts data to the database (duh)
      */
    def createChannelSink(channel: Int, channelRecordingId: Long): Sink[Task, Int] = {

      def go: Handle[Task,Int] => Pull[Task,Unit,Unit] = h => {
        h.awaitN(1024, false) flatMap {
          case (chunks, h) => {
            val folded = (chunks.foldLeft(Vector.empty[Int])(_ ++ _.toVector)).toArray
            val insert: Task[Int] = doobieTasks.doobieWriters.insertDataRecord(channelRecordingId, folded)

            Pull.outputs(eval_(insert)) >> go(h)
          }
        }
      }
      _.pull(go)
    }


    /**
      Starts pumping in ze data!
      */
    def startRecording(topics: List[dataTopic[Task]], id: ExperimentId)(implicit s: fs2.Strategy): Stream[Task,Unit] = {
      val channels = (0 to 60).map(createChannelSink(_, id))
      val huh = channels zip topics map{
        case(channelSink: Sink[Task,Int], topic) => {
          topic.subscribe(200)
            .map(λ => λ._1)
            .through(chunkify)
            .through(channelSink)
        }
      }
      concurrent.join(60)(Stream.emits(huh).covary[Task])
    }
  }
}
