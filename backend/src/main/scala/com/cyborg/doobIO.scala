package com.cyborg

import cats._, cats.data._, cats.implicits._
import doobie.imports._
import fs2.interop.cats._

import shapeless._
import shapeless.record.Record

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._

object memeStorage {

  val superdupersecretPassword = ""

  val xa = DriverManagerTransactor[fs2.Task](
    "org.postgresql.Driver",
    "jdbc:postgresql:world",
    "postgres",
    s"$superdupersecretPassword"
  )

  val experimentId = 1

  case class ChannelRecording(experimentId: Long, channelRecordingId: Long, channelNumber: Long)

  val channelStream: ConnectionIO[List[fs2.Stream[fs2.Task, Array[Byte]]]] =
    for {
      a <-
        sql"""
          SELECT channelRecordingId
          FROM channelRecording
          WHERE channelRecordingId = $experimentId
        """.query[ChannelRecording].list
    } yield (a.map (token =>
        sql"""
          SELECT sample
          FROM datapiece
          WHERE channelRecordingId = ${token.channelRecordingId}
          ORDER BY index
        """.query[Array[Byte]].process.transact(xa)))


  val test: fs2.Task[List[fs2.Stream[fs2.Task, Array[Byte]]]] = channelStream.transact(xa)
  val channelStreams: fs2.Stream[fs2.Task, List[fs2.Stream[fs2.Task, Array[Byte]]]] = fs2.Stream.eval(test)

  // val test3 = test2.flatMap ( channelStreamList =>
  //   {
  //     val unpacked = channelStreamList.map(
  //       (singleChannelStream: fs2.Stream[fs2.Task, Array[Byte]]) => {
  //         singleChannelStream.through(utilz.arrayBreaker(512))
  //       })
  //     ???
  //   }
  // )

}
