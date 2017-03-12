package com.cyborg

import com.github.nscala_time.time.Imports._

import fs2.util.Async
import java.nio.file.Paths
import fs2.io.tcp._
import fs2._
import params._

import spray.json._
import fommil.sjs.FamilyFormats._

object FW {

  def meameWriter[F[_]: Async](params: NeuroDataParams, meameSocket: Socket[F]): Stream[F, Unit] = {

    // TODO should be in params, but won't compile because of arcane reasons
    implicit val modelFormat = jsonFormat4(NeuroDataParams.apply)

    val fileName = s"recording ${DateTime.now().toString}"
    val JSONparams = params.toJson

    val paramString: Stream[Pure, Byte] = Stream.emit(JSONparams.compactPrint + "\n").through(text.utf8Encode)

    val reads: Stream[F, Byte] = meameSocket.reads(1024*1024)

    val thing = (paramString ++ reads)
      .through(io.file.writeAll(Paths.get(s"/home/peter/MEAMEdata/$fileName")))

    thing
  }


  // TODO this looks very wrong to me
  def meameReader[F[_]: Async](filename: String): Stream[F, (Stream[F, NeuroDataParams], Stream[F, Byte])] = {

    val memeLord = io.file.readAll[F](Paths.get(filename), 4096)
      .through(text.utf8Decode)
      .through(text.lines)

    def toparams(s: String): NeuroDataParams = {
      // TODO should be in params, but won't compile because of arcane reasons
      implicit val modelFormat = jsonFormat4(NeuroDataParams.apply)

      val fug = s.parseJson
      val meme = fug.convertTo[NeuroDataParams]
      meme
    }

    val params = memeLord.through(pipe.take(1)).through(_.map(toparams))
    val data = memeLord.through(pipe.drop(1)).through(text.utf8Encode)

    val meme: Stream[F, (Stream[F, NeuroDataParams], Stream[F, Byte])] = Stream.emit((params, data))
    meme
  }
}
