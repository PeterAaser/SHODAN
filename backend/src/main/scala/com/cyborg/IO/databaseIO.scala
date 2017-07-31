package com.cyborg

import fs2._
import fs2.Stream._

import scala.language.higherKinds

object databaseIO {

  /**
    ???
    */
  def arrayBreaker[F[_]](chunkSize: Int): Pipe[F,Array[Int], Int] = {
    def get: Handle[F,Array[Int]] => Pull[F,Int,Unit] = h => {
      h.receive1 {
        (v, h) => {
          println(" !! Breaking Array !! ")
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
  }

}
