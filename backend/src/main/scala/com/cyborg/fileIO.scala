package com.cyborg

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import fs2.concurrent

import fs2.util.Async
import java.io.File
import java.nio.file.Paths
import fs2.io.tcp._
import fs2._
import params._

import spray.json._
import fommil.sjs.FamilyFormats._

object FW {


  def getListOfFiles(dir: String): List[File] =
    (new File(dir)).listFiles.filter(_.isFile).toList


  val fmt = DateTimeFormat.forPattern("dd.MM.yyyy, HH:mm:ss")
  def timeString = DateTime.now().toString(fmt)


  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  def sortFilesByDate(files: List[File]) =
    files.map(_.getName).map(DateTime.parse(_, fmt)).sorted


  def getNewestFilename: String =
    sortFilesByDate(getListOfFiles("/home/peter/MEAMEdata"))
      .head.toString(fmt)


  def meameDataWriter[F[_]: Async](params: NeuroDataParams, meameSocket: Socket[F]): Stream[F, Unit] = {

    // TODO should be in params, but won't compile because of arcane reasons
    implicit val modelFormat = jsonFormat4(NeuroDataParams.apply)

    val time = timeString

    val fileName = s"$time"
    val JSONparams = params.toJson

    val paramString: Stream[Pure, Byte] = Stream.emit(JSONparams.compactPrint + "\n").through(text.utf8Encode)
    val reads: Stream[F, Byte] = meameSocket.reads(1024*1024)

    val meme = (paramString ++ reads)
      .through(io.file.writeAll(Paths.get(s"/home/peter/MEAMEdata/$fileName")))

    meme
  }


  // TODO this looks very wrong to me
  def meameDataReader[F[_]: Async](filename: String): Stream[F, (Stream[F, NeuroDataParams], Stream[F, Byte])] = {

    def toparams(s: String): NeuroDataParams = {
      // TODO should be in params, but won't compile because of arcane reasons
      implicit val modelFormat = jsonFormat4(NeuroDataParams.apply)

      val params = s.parseJson
      val paramJson = params.convertTo[NeuroDataParams]
      paramJson
    }

    val paramFileStream = io.file.readAll[F](Paths.get(s"/home/peter/MEAMEdata/params/params"), 4096)
      .through(text.utf8Decode)
      .through(text.lines)

    val dataFileStream = io.file.readAll[F](Paths.get(s"/home/peter/MEAMEdata/$filename"), 4096)
    val params = paramFileStream.through(_.map(toparams))

    val meme: Stream[F, (Stream[F, NeuroDataParams], Stream[F, Byte])] =
      Stream.emit((params, dataFileStream))


    meme
  }

  def meameLogWriter[F[_]: Async](log: Stream[F, Byte]): F[Unit] = {

    val time = timeString

    val meme = log.through(io.file.writeAllAsync(Paths.get(s"/home/peter/MEAMEdata/log/$timeString"))).drain
    meme.run
  }

  // From a bunch of sinks run the data through dem sinks
  def genericChannelSplitter[F[_]: Async](infile: String, sinks: F[List[Sink[F,Byte]]]): F[Unit] = {
    val pointsPerSweep = 1024

    val inStream = for {
      instream <- meameDataReader(infile)
      dataStream <- instream._2.through(utilz.bytesToInts)
    } yield dataStream

    val channelStreams = utilz.alternate(inStream, pointsPerSweep, 256*256*8, 60)

    def writeChannelData(sink: Sink[F, Byte], dataStream: Stream[F,Vector[Int]]) =
      dataStream
        .through(utilz.chunkify)
        .through(utilz.intsToBytes)
        .through(sink)

    val writeTaskStream = channelStreams flatMap {
      channels: List[Stream[F,Vector[Int]]] => {
        Stream.eval(sinks) flatMap {
          sinks: List[Sink[F,Byte]] => {
            val a =
              ((channels zip sinks)
                 .map( { case (channel, sink) => (writeChannelData(sink, channel)) } ))

            Stream.emits(a)
          }
        }
      }
    }

    concurrent.join(200)(writeTaskStream).drain.run
  }

  // Takes a file, splits it into a set of files
  def channelSplitter[F[_]: Async](filename: String): F[Unit] = {

    val pointsPerSweep = 1024

    val inStream = for {
      instream <- meameDataReader(filename)
      dataStream <- instream._2.through(utilz.bytesToInts)
    } yield dataStream

    val channelStreams = utilz.alternate(inStream, pointsPerSweep, 256*256, 60)

    def writeChannelData(filename: String, dataStream: Stream[F, Vector[Int]]) =
      dataStream
        .through(utilz.chunkify)
        .through(utilz.intsToBytes)
        .through(io.file.writeAllAsync(Paths.get(s"/home/peter/MEAMEdata/channels/$filename")))

    def channelName(n: Int): String =
      s"channel_$n"

    val writeTaskStream = channelStreams flatMap {
      channels: List[Stream [F,Vector[Int]]] => {
        val a = channels.zipWithIndex
          .map( { case (λ, µ) => (λ, channelName(µ)) } )
          .map( { case (λ, µ) => (writeChannelData(µ, λ)) } )

        Stream.emits(a)
      }
    }

    val meme = concurrent.join(200)(writeTaskStream)

    meme.run
  }
}
