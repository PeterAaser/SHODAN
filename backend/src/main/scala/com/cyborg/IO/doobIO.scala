package com.cyborg

import cats._, cats.data._, cats.implicits._
import cats.effect.IO

import doobie.imports._
import doobie.postgres.imports._
import fs2._

import shapeless._
import shapeless.record.Record

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._

object doobieTasks {

  val superdupersecretPassword = ""

  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:fug",
    "postgres",
    s"$superdupersecretPassword"
  )

  case class ChannelRecording(experimentId: Long, channelRecordingId: Long, channelNumber: Long)
  case class ExperimentInfo(id: Long, timestamp: DateTime, comment: Option[String])
  case class ExperimentParams(sampleRate: Int)

  object doobieReaders {

    def selectChannelStreams(experimentId: Int, channels: List[Int]): IO[List[Stream[IO,Array[Int]]]] = {

      def getChannels(experimentId: Int): ConnectionIO[List[ChannelRecording]] =
        sql"""
       SELECT experimentId, channelRecordingId, channelNumber
       FROM channelRecording
       WHERE channelRecordingId = $experimentId
     """.query[ChannelRecording].list

      def getChannelStream(recording: ChannelRecording): Stream[IO,Array[Int]] = {
        sql"""
       SELECT sample
       FROM datapiece
       WHERE channelRecordingId = ${recording.channelRecordingId}
       ORDER BY index
     """.query[Array[Int]].process.transact(xa)
      }

      def selectChannelStreams(recordings: List[ChannelRecording], channels: List[Int]) = {
        val requested = recordings.filter( recording => !channels.contains(recording.channelNumber.toInt) )
        requested.map(getChannelStream(_))
      }

      val dbio = for {
        a <- getChannels(experimentId)
      } yield (selectChannelStreams(a, channels))

      dbio.transact(xa)
    }

    def getExperimentInfo(experimentId: Int): Stream[IO,ExperimentParams] =
      Stream(ExperimentParams(40000))
  }

  object doobieWriters {

    // Creates a sink for a channel inserting DATAPIECES
    def channelSink(channel: Int, channelRecordingId: Long): Sink[IO, Int] = {
      def go(s: Stream[IO, Int]): Pull[IO, IO[Int], Unit] = {
        s.pull.unconsN(1024, false) flatMap {
          case Some((segment, tl)) => {
            val folded = segment.toArray
            val insert: IO[Int] = {
              sql"""
              INSERT INTO datapiece (channelRecordingId, sample)
              VALUES ($channelRecordingId, $folded)
            """.update.run.transact(xa)
            }
            Pull.output1(insert) >> go(tl)
          }
        }
      }
      in => go(in).stream.drain
    }

    def insertDataRecord(channelRecordingId: Long, data: Array[Int]): IO[Int] = {
      sql"""
      INSERT INTO datapiece (channelRecordingId, sample)
      VALUES ($channelRecordingId, $data)
    """.update.run.transact(xa)
    }

    def insertNewExperiment(comment: Option[String]): IO[Long] = {
      val comment_ = comment.getOrElse("no comment")
      (for {
        _ <- sql"INSERT INTO experimentInfo (comment) VALUES ($comment_)".update.run
        id <- sql"select lastval()".query[Long].unique
      } yield (id)).transact(xa)
    }

    def insertChannel(experimentId: Long, channel: Int): ConnectionIO[Long] = {
      for {
        _ <- sql"INSERT INTO channelRecording (experimentId, channelNumber) VALUES ($experimentId, $channel)".update.run
        id <- sql"select lastval()".query[Long].unique
      } yield (id)
    }

    // Inserts a bunch of channels to an experiment
    def insertChannels(experimentId: Long): IO[List[Long]] = {
      val insertionTasks: List[ConnectionIO[Long]] = Range(0, 60).toList.map( i => insertChannel(experimentId, i) )
      insertionTasks.sequence.transact(xa)
    }
  }
}
