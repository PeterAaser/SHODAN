package com.cyborg

import fs2._
import fs2.Stream._
import cats.effect.IO
import scala.concurrent.ExecutionContext
import utilz._

import scala.language.higherKinds

object databaseIO {

  type ExperimentId = Long

  object dbReaders {

    /**
      Breaks bigass database chunks into something more manageable
      */
    def arrayBreaker[F[_]](chunkSize: Int): Pipe[F,Array[Int], Int] = {
      def get(s: Stream[F, Array[Int]]): Pull[F,Int,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((d, tl)) => {
            val grouped = d.grouped(chunkSize).toList
            unload(grouped, tl)
          }
        }
      }
      def unload(dudes: List[Array[Int]], tl: Stream[F,Array[Int]]): Pull[F,Int,Unit] = {
        dudes match {
          case head :: t => Pull.output(Chunk.seq(head)) >> unload(t, tl)
          case _ => get(tl)
        }
      }
      in => get(in).stream
    }


    /**
      Reads from the database, returning a stream of integers as if they were collected live.
      This means we have to demux them all over.
      */
    def dbChannelStream(experimentId: Int): Stream[IO, Int] = {

      // This is OK because we only multiplex, thus segmentLength when recorded is irrelevant.
      import params.experiment.segmentLength

      // A list of streams from each channel
      val dbChannelStreams: Stream[IO, List[Stream[IO, Array[Int]]]] =
        Stream.eval(doobieTasks.doobieReaders.selectChannelStreams(experimentId, (0 to 60).toList))

      // An unpacking function breaking down huge pieces of database data
      def unpack = (s: Stream[IO,Array[Int]]) =>
      s.through(arrayBreaker(segmentLength)).through(utilz.vectorize(segmentLength))

      dbChannelStreams
        .map(_.map(_.through(unpack)))
        .through(utilz.roundRobin)
        .through(utilz.chunkify)
        .through(utilz.chunkify)
    }

    def getExperimentSampleRate(experimentId: Int): Stream[IO,Int] =
      doobieTasks.doobieReaders.getExperimentInfo(experimentId).map(_.sampleRate)
  }

  object dbWriters {

    /**
      Creates a new experiment and sets up channels etc
      */
    def createExperiment(comment: Option[String]): IO[ExperimentId] = {
      for {
        id <- doobieTasks.doobieWriters.insertNewExperiment(comment)
        _ <- doobieTasks.doobieWriters.insertChannels(id)
      } yield (id)
    }


    /**
      Creates a sink that inserts data to the database (duh)
      */
    def createChannelSink(channel: Int, channelRecordingId: Long): Sink[IO, Int] = {

      def go(s: Stream[IO, Int]): Pull[IO,Unit,Unit] = {
        s.pull.unconsN(1024, false) flatMap {
          case Some((segment, tl)) => {
            val insert: IO[Int] = doobieTasks.doobieWriters.insertDataRecord(channelRecordingId, segment.toArray)

            Pull.eval(insert) >> go(tl)
          }
        }
      }
      in => go(in).stream
    }


    /**
      Starts pumping in ze data!
      TODO explodes
      */
    def startRecording(topics: List[dataTopic[IO]], id: ExperimentId)(implicit ec: ExecutionContext): Stream[IO,Unit] = {
      val channels = (0 to 60).map(createChannelSink(_, id))
      val huh = channels zip topics map{
        case(channelSink: Sink[IO,Int], topic) => {
          topic.subscribe(200)
            .map(λ => λ._1)
            .through(chunkify)
            .through(channelSink)
        }
      }
      // TODO are these the same?
      // concurrent.join(60)(Stream.emits(huh).covary[IO])
      Stream.emits(huh).join(60)
    }
  }
}
