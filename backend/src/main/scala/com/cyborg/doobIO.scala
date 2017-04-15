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

object databaseTasks {

  val superdupersecretPassword = ""

  val xa = DriverManagerTransactor[Task](
    "org.postgresql.Driver",
    "jdbc:postgresql:fug",
    "postgres",
    s"$superdupersecretPassword"
  )

  val experimentId = 4
  case class ChannelRecording(experimentId: Long, channelRecordingId: Long, channelNumber: Long)

  def getChannel(experimenId: Long, channelNumber: Long): Stream[ConnectionIO,ChannelRecording] =
    sql"""
      SELECT experimentId, channelRecordingId, channelNumber
      FROM channelRecording
      WHERE experimentId = $experimentId AND channel = $channelNumber
    """.query[ChannelRecording].process

  def getChannels(experimentId: Long, channels: List[Long]): List[Stream[ConnectionIO,ChannelRecording]] =
    channels.map(getChannel(experimentId, _))

  def getDataStream(channelRecording: ChannelRecording): Stream[ConnectionIO,Array[Int]] =
    sql"""
      SELECT sample
      FROM datapiece
      WHERE channelRecordingId = ${channelRecording.channelRecordingId}
      ORDER BY index
    """.query[Array[Int]].process


  val hai2u: List[Stream[ConnectionIO,ChannelRecording]] = getChannels(experimentId, List(4, 5, 6, 7))

  def stupidThing(xs: List[Stream[ConnectionIO,ChannelRecording]]): Stream[ConnectionIO,ChannelRecording] =
    xs match {
      case scala.collection.immutable.::(h,t) => h.flatMap { λ => stupidThing(t) ++ Stream.emit(λ) }
      case _ => Stream.empty
    }
  val hai3u: Stream[ConnectionIO,ChannelRecording] = stupidThing(hai2u)

  def _channelStream: ConnectionIO[List[Stream[ConnectionIO, Array[Int]]]] = {

    val channels: ConnectionIO[List[ChannelRecording]] =
      sql"""
        SELECT experimentId, channelRecordingId, channelNumber
        FROM channelRecording
        WHERE experimentId = $experimentId
      """.query[ChannelRecording].list

    val queries: ConnectionIO[List[Stream[ConnectionIO,Array[Int]]]] = channels map {
      channelList: List[ChannelRecording] => {

        val dataqueries: List[Stream[ConnectionIO,Array[Int]]] =
          channelList.map( channelToken =>
            sql"""
              SELECT sample
              FROM datapiece
              WHERE channelRecordingId = ${channelToken.channelRecordingId}
              ORDER BY index
            """.query[Array[Int]].process)

        dataqueries
      }
    }
    queries
  }

  val testing: Task[List[Stream[ConnectionIO,Array[Int]]]] = _channelStream.transact(xa)
  val test2: Stream[Task,List[Stream[ConnectionIO,Array[Int]]]] = Stream.eval(testing)
  val test3: Stream[Task,List[Stream[Task,Array[Int]]]] = test2.through(_.map(_.map(_.transact(xa))))

  val channelStream: ConnectionIO[List[Stream[Task, Array[Int]]]] = {

    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")
    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")
    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")
    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")
    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")

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
        """.query[Array[Int]].process.transact(xa))} }

  val test: Task[List[Stream[Task, Array[Int]]]] =
    channelStream.transact(xa)

  val channelStreams: Stream[Task, List[Stream[Task, Array[Int]]]] =
    Stream.eval(test)


  // Oh dog what done
  // TODO FIX ME!!!!!!!!!!!!!!!!!!!!
  def filteredChannelStream(channels: List[Int]): ConnectionIO[List[Stream[Task, Array[Int]]]] = {

    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")
    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")
    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")
    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")
    println(" !! WOW REMINDER THAT THIS ULTRACLUSTERFUCK OF A FUNCTION IS STILL IN →→→→ ACTIVE ←←←← USE !! ")

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
               """.query[Array[Int]].process.transact(xa) }))
  }

  val wanted = List(31, 32, 33, 34)
  val filteredChannelStreamTask: Task[List[Stream[Task, Array[Int]]]] =
    filteredChannelStream(wanted).transact(xa)

  // good naming scheme, might as well go back to "fug", "meme" and the likes
  val filteredChannelStreams: Stream[Task,List[Stream[Task,Array[Int]]]] = Stream.eval(filteredChannelStreamTask)

  // Creates a sink for a channel inserting DATAPIECES
  // TODO should maybe be F, but contains a transact, probably bad form.
  // TODO maybe it should be on form Transactor[F] => Sink[F,Int] ???
  def channelSink(channel: Int, channelRecordingId: Long): Sink[Task, Int] = {
    def go: Handle[Task,Int] => Pull[Task,Task[Int],Unit] = h => {
      h.awaitN(1024, false) flatMap {
        case (chunks, h) => {
          val folded = (chunks.foldLeft(Vector.empty[Int])(_ ++ _.toVector)).toArray
          val insert: Task[Int] = {
            println("Inserting 1024 pieces")
            sql"""
              INSERT INTO datapiece (channelRecordingId, sample)
              VALUES ($channelRecordingId, $folded)
            """.update.run.transact(xa)
          }
          Pull.output1(insert) >> go(h)
        }
      }
    }
    _.pull(go).flatMap{ λ: Task[Int] => Stream.eval_(λ) }
  }

  case class ExperimentInfo(id: Long, timestamp: DateTime, comment: Option[String])

  // TODO get rid of obnoxious print clutter
  def insertNewExperiment(comment: Option[String]): ConnectionIO[Long] = {
    val comment_ = comment.getOrElse("no comment")
    for {
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
  def setupExperimentStorage: Task[List[Sink[Task,Int]]] = {
    println("I am setting up storage for an experiment")
    val sinks: ConnectionIO[List[Sink[Task,Int]]] = for {
      experimentId <- insertNewExperiment(Some("Test recording 15 april"))
      channelIds <- insertChannels(experimentId)
    } yield (channelIds.zipWithIndex.map { case (id, i) => channelSink(i, id) })
    println("Ok, storage is gucci")
    sinks.transact(xa)
  }
}
