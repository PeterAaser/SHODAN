package com.cyborg

import cats._, cats.data._, cats.implicits._
import doobie.imports._
import doobie.postgres.imports._
import fs2.interop.cats._
import fs2._

import shapeless._
import shapeless.record.Record

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._

object memeStorage {

  val superdupersecretPassword = ""

  val xa = DriverManagerTransactor[fs2.Task](
    "org.postgresql.Driver",
    "jdbc:postgresql:fug",
    "postgres",
    s"$superdupersecretPassword"
  )

  val experimentId = 4

  case class ChannelRecording(experimentId: Long, channelRecordingId: Long, channelNumber: Long)

  val channelStream: ConnectionIO[List[Stream[Task, Array[Byte]]]] = {
    println(" ??? ")
    for {
      a <- {
        sql"""
          SELECT experimentId, channelRecordingId, channelNumber
          FROM channelRecording
          WHERE experimentId = $experimentId
        """.query[ChannelRecording].list
      }
      _ = println(a)
    } yield {
      println(" hlep ")
      a.map (token =>
        sql"""
          SELECT sample
          FROM datapiece
          WHERE channelRecordingId = ${token.channelRecordingId}
          ORDER BY index
        """.query[Array[Byte]].process.transact(xa))} }

  val test: Task[List[Stream[Task, Array[Byte]]]] =
    channelStream.transact(xa)

  val channelStreams: Stream[Task, List[Stream[Task, Array[Byte]]]] =
    Stream.eval(test)


  def filteredChannelStream(channels: List[Int]): ConnectionIO[List[Stream[Task, Array[Byte]]]] = {
    println(" !! making a filtered channel stream !! ")
    for {
      a <-
      sql"""
          SELECT experimentId, channelRecordingId, channelNumber
          FROM channelRecording
          WHERE channelRecordingId = $experimentId
        """.query[ChannelRecording].list
    } yield (a.filter(λ => !channels.contains(λ.channelNumber.toInt)))
      .map( (token => {
             println(" ~~~~~ hi :DDD ~~~~~ ")
               sql"""
                 SELECT sample
                 FROM datapiece
                 WHERE channelRecordingId = ${token.channelRecordingId}
                 ORDER BY index
               """.query[Array[Byte]].process.transact(xa) }))
  }

  val wanted = List(31, 32, 33, 34)
  val filteredChannelStreamTask: Task[List[Stream[Task, Array[Byte]]]] =
    filteredChannelStream(wanted).transact(xa)

  // good naming scheme, might as well go back to "fug", "meme" and the likes
  val filteredChannelStreams: Stream[Task,List[Stream[Task,Array[Byte]]]] = Stream.eval(filteredChannelStreamTask)

  // Creates a sink for a channel inserting DATAPIECES
  def channelSink(channel: Int, channelRecordingId: Long): Sink[Task, Byte] = {
    def go: Handle[Task,Byte] => Pull[Task,Task[Int],Unit] = h => {
      h.awaitN(12000, false) flatMap {
        case (chunks, h) => {
          val folded = (chunks.foldLeft(Vector.empty[Byte])(_ ++ _.toVector)).toArray
          val insert: Task[Int] = {
            println("Inserting 10000 pieces")
            sql"""
              INSERT INTO datapiece (channelRecordingId, sample)
              VALUES ($channelRecordingId, $folded)
            """.update.run.transact(xa)
          }
          Pull.output1(insert) >> go(h)
        }
      }
    }
    λ => {
      val meme = λ.pull(go).flatMap{ λ: Task[Int] => Stream.eval_(λ) }
      meme.drain
    }
  }

  import com.github.nscala_time.time.Imports._
  import com.github.nscala_time.time.Implicits._
  val fmt = DateTimeFormat.forPattern("dd.MM.yyyy, HH:mm:ss")
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)

  case class ExperimentInfo(id: Long, timestamp: DateTime, comment: Option[String])

  def insertNewExperiment(comment: Option[String]): ConnectionIO[Long] = {
    val comment_ = comment.getOrElse("no comment")
    val meme = for {
      _ <- {
        println(" ~~ Inserting new experiment ~~ ");
        sql"INSERT INTO experimentInfo (comment) VALUES ($comment_)".update.run
      }
      id <- {
        println(" ~~ Experiment inserted, attempting to retrieve ~~ ");
        sql"select lastval()".query[Long].unique
      }
      _ = println(s" ~~ Experiment inserted with id $id ~~ ")
    } yield (id)
    meme
  }

  def insertChannel(experimentId: Long, channel: Int): ConnectionIO[Long] = {
    for {
      _ <- {
        println(s" ~~ Inserting Channel $channel for experiment $experimentId ~~ ")
        sql"INSERT INTO channelRecording (experimentId, channelNumber) VALUES ($experimentId, $channel)".update.run
      }
      id <- sql"select lastval()".query[Long].unique
    } yield (id)
  }

  def insertChannels(experimentId: Long): ConnectionIO[List[Long]] = {
    println(s"Inserting channels for $experimentId")
    val meme: List[ConnectionIO[Long]] = Range(0, 60).toList.map( i => insertChannel(experimentId, i) )
    val meme2: ConnectionIO[List[Long]] = meme.sequence
    meme2
  }

  // Inserts an experiment, creates a bunch of sinks
  def setupExperimentStorage: Task[List[Sink[Task,Byte]]] = {
    println("I am setting up storage for an experiment")
    val sinks: ConnectionIO[List[Sink[Task,Byte]]] = for {
      experimentId <- insertNewExperiment(Some("forsok3"))
      channelIds <- insertChannels(experimentId)
    } yield (channelIds.zipWithIndex.map { case (id, i) => channelSink(i, id) })
    println("Ok, storage is gucci")
    sinks.transact(xa)
  }
}
