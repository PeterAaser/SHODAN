package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.Async

import scala.language.higherKinds

object DatabaseIO {

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

  // val filteredChannelStreams: Stream[Task,List[Stream[Task,Array[Byte]]]] = Stream.eval(filteredChannelStreamTask)
  def databaseStream[F[_]: Async](fcs: Stream[F,List[Stream[F,Array[Int]]]]): Stream[F,Int] = fcs flatMap (channelStreamList =>
    {
      val unpacked: List[Stream[F, Vector[Int]]] =
        channelStreamList.map(_.through(arrayBreaker(512)).through(utilz.vectorize(1024*4)))

      val spliced: Stream[F, Vector[Int]] =
        (Stream[F, Vector[Int]](Vector[Int]()).repeat /: unpacked){
          (b: Stream[F, Vector[Int]], a: Stream[F, Vector[Int]]) => b.zipWith(a){
            (λ, µ) => µ ++: λ
          }
        }

      val rechunked: Stream[F, Int] =
        spliced.through(utilz.chunkify)

      rechunked
    }
  )
}
