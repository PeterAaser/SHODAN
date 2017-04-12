package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.Async

import scala.language.higherKinds

object DatabaseIO {

  def arrayBreaker[F[_]](chunkSize: Int): Pipe[F,Array[Byte], Byte] = {
    def get: Handle[F,Array[Byte]] => Pull[F,Byte,Unit] = h => {
      h.receive1 {
        (v, h) => {
          println(" !! Breaking Array !! ")
          val grouped = v.grouped(chunkSize).toList
          unload(grouped)(h)
        }
      }
    }
    def unload(dudes: List[Array[Byte]]): Handle[F,Array[Byte]] => Pull[F,Byte,Unit] = h => {
      dudes match {
        case head :: t => Pull.output(Chunk.seq(head)) >> unload(t)(h)
        case _ => get(h)
      }
    }
    _.pull(get)
  }

  val databaseStream: Stream[Task,Byte] = memeStorage.channelStreams flatMap (channelStreamList =>
    {
      val unpacked: List[Stream[Task, Vector[Byte]]] =
        channelStreamList.map(_.through(arrayBreaker(512)).through(utilz.vectorize(1024*4)))

      val spliced: Stream[Task, Vector[Byte]] =
        (Stream[Task, Vector[Byte]](Vector[Byte]()).repeat /: unpacked){
          (b: Stream[Task, Vector[Byte]], a: Stream[Task, Vector[Byte]]) => b.zipWith(a){
            (λ, µ) => µ ++: λ
          }
        }

      val rechunked: Stream[Task, Byte] =
        spliced.through(utilz.chunkify)

      rechunked
    }
  )

  import params._
  // TODO this looks very wrong to me
  def meameDatabaseReader[F[_]: Async](filename: String, dbStream: Stream[F,Byte]): Stream[F, (Stream[F, NeuroDataParams], Stream[F, Byte])] = {

    import spray.json._
    import fommil.sjs.FamilyFormats._

    import java.nio.file.Paths

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
      Stream.emit((params, dbStream))

    meme
  }
}
