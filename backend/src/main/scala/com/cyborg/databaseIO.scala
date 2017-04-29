package com.cyborg

import fs2._
import fs2.Stream._

import scala.language.higherKinds

object databaseIO {

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


  def dbChannelStream(channels: List[Int], experimentId: Int): Stream[Task, Int] = {
    val meme = Stream.eval(doobieTasks.selectChannelStreams(3, List(1, 2, 3)))
    meme flatMap (channelStreamList =>
      {
        val unpacked: List[Stream[Task, Vector[Int]]] =
          channelStreamList.map(_.through(arrayBreaker(512)).through(utilz.vectorize(1024*4)))

        val spliced: Stream[Task, Vector[Int]] =
          (Stream[Task, Vector[Int]](Vector[Int]()).repeat /: unpacked){
            (b: Stream[Task, Vector[Int]], a: Stream[Task, Vector[Int]]) => b.zipWith(a){
              (λ, µ) => µ ++: λ
            }
          }

        val rechunked: Stream[Task, Int] =
          spliced.through(utilz.chunkify)

        rechunked
      }
    )
  }

}
