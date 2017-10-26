package cyborg

import cats.effect.Effect
import fs2._
import fs2.Stream._
import cats.effect.IO
import fs2.async.mutable.Queue
import scala.concurrent.ExecutionContext
import utilz._
import com.github.nscala_time.time.Imports._

import scala.language.higherKinds

object databaseIO {

  type ExperimentId = Long

  object dbReaders {

    /**
      Breaks bigass database chunks into something more manageable
      */
    // TODO should use queues
    def arrayBreaker[F[_]: Effect](chunkSize: Int, queue: Queue[F,Array[Int]])(implicit ec: ExecutionContext): Pipe[F,Array[Int], Int] = {

      def get(s: Stream[F, Array[Int]]): Pull[F,Int,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((d, tl)) => {
            // println(s"arrayBreaker breaking array sized ${d.size}")
            val grouped = d.grouped(chunkSize).toList
            unload(grouped, tl)
          }
          case None => Pull.done
        }
      }
      def unload(dudes: List[Array[Int]], tl: Stream[F,Array[Int]]): Pull[F,Int,Unit] = {
        dudes match {
          case head :: t => {
            println(head.size)
            Pull.output(Chunk.seq(head)) >> unload(t, tl)
          }
          case _ => {
            // println("arrayBreaker getting next array")
            get(tl)
          }
        }
      }

      in => {
        val inStream = in.through(queue.enqueue)
        val outStream = get(queue.dequeue).stream
        outStream.concurrently(inStream)
      }
    }


    /**
      Reads from the database, returning a stream of integers as if they were collected live.
      This means we have to demux them all over.
      */
    // TODO tested in dIO
    def dbChannelStream(experimentId: Int)(implicit ec: ExecutionContext): Stream[IO, Int] = {

      // This is OK because we only multiplex, thus segmentLength when recorded is irrelevant.
      import params.experiment.segmentLength

      // A list of streams from each channel
      val dbChannelStreams: IO[List[Stream[IO, Array[Int]]]] =
        doobieTasks.doobieReaders.selectChannelStreams(experimentId, (0 until 60).toList)

      Stream.eval(fs2.async.boundedQueue[IO,Array[Int]](4)) flatMap { queue =>
        // An unpacking function breaking down huge pieces of database data
        def unpack(s: Stream[IO,Array[Int]]) = s
          .through(arrayBreaker(segmentLength, queue)).through(utilz.vectorize(segmentLength))

        Stream.eval(dbChannelStreams) flatMap { streams =>
          val mepped = streams.map(_.through(unpack).through(utilz.chunkify))
          utilz.roundRobinL(mepped).through(utilz.chunkify)
        }
      }

    }

    def whatTheFuck(experimentId: Int): Stream[IO,Int] = {
      val dbChannelStreams = doobieTasks.doobieReaders.selectChannelStreams(1, List(1))
      Stream.eval(dbChannelStreams) flatMap {
        streams => {
          println(streams(0))
          streams(0).through(_.map(_.toList)).through(utilz.chunkify)
        }
      }
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
      Creates a new experiment for an old recording and sets up channels etc
      */
    def createOldExperiment(comment: Option[String], date: DateTime): IO[ExperimentId] = {
      for {
        id <- doobieTasks.doobieWriters.insertOldExperiment(comment, date)
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
          case None => Pull.done
        }
      }
      in => go(in).stream
    }


    /**
      Starts pumping in ze data!
      TODO should probably be removed?
      */
    def startRecording(topics: List[DataTopic[IO]], id: ExperimentId)(implicit ec: ExecutionContext): Stream[IO,Unit] = {
      val channels = (0 until 60).map(createChannelSink(_, id))
      val huh = channels zip topics map{
        case(channelSink: Sink[IO,Int], topic) => {
          topic.subscribe(200)
            .map(λ => λ._1)
            .through(chunkify)
            .through(channelSink)
        }
      }
      Stream.emits(huh).join(60)
    }
  }
}
