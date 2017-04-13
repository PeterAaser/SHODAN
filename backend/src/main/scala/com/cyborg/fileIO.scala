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


  // TODO slated for deletion, kept around to reproduce some data mangling issues. Yes I've heard of git, fuck you
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


  def meameDataReader[F[_]: Async](filename: String): Stream[F,Int] = {
    val dataFileStream =
      io.file.readAll[F](Paths.get(s"/home/osboxes/MEAMEdata/$filename"), 4096)
        .through(utilz.bytesToInts)

    dataFileStream
  }


  // TODO no reason to exist in its current form. Flat files are bad.
  def meameLogWriter[F[_]: Async](log: Stream[F, Byte]): F[Unit] = {

    val time = timeString

    val meme = log.through(io.file.writeAllAsync(Paths.get(s"/home/peter/MEAMEdata/log/$timeString"))).drain
    meme.run
  }

  // From a bunch of sinks run the data through dem sinks
  // TODO does this have to be Int? Is it more generic?
  def genericChannelSplitter[F[_]: Async](infile: String, sinks: F[List[Sink[F,Int]]]): F[Unit] = {
    val pointsPerSweep = 1024

    val inStream: Stream[F,Int] = meameDataReader(infile)

    val channelStreams = utilz.alternate(inStream, pointsPerSweep, 256*256*8, 60)

    def writeChannelData(sink: Sink[F,Int], dataStream: Stream[F,Vector[Int]]) =
      dataStream
        .through(utilz.chunkify)
        .through(sink)

    val writeTaskStream = channelStreams flatMap {
      channels: List[Stream[F,Vector[Int]]] => {
        Stream.eval(sinks) flatMap {
          sinks: List[Sink[F,Int]] => {
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
}
