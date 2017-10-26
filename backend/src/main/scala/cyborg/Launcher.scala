package cyborg

import fs2._
import fs2.async.mutable.Topic
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import backendImplicits._

import cats.effect._
import utilz._

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import java.io.File
import java.nio.file.Paths

import backendImplicits._

object Launcher {
  def main(args: Array[String]): Unit = {

    println("wello")

    params.printParams()

    Assemblers.startSHODAN.run.unsafeRunSync()

    import params.experiment._
    // val dur = utilz.sineWave[IO](60, segmentLength).take(60*segmentLength*2).runLog.unsafeRunSync()
    // val uno = dur.drop(segmentLength).take(segmentLength)
    // val doz = dur.drop(segmentLength*60).drop(segmentLength).take(segmentLength)

    // println(uno)
    // println(doz)

    // val topics = Assemblers.assembleTopics[IO]

    // TODO: Destroy ↓
    // A mistake
    // val memeStream: Stream[IO,Stream[IO,Unit]] = Stream.emits(0 to 60).covary[IO].map { i =>
    //   val volt_base = -1000 + ((i/60)*2000)
    //   val fileName = f"${i}%02d"
    //   val streamLine = Stream.emits(List.fill(1000)(volt_base)).covary[IO].repeat
    //     .take(1000000)
    //     .through(_.map(λ => s"$λ,"))
    //     .intersperse("\n")
    //     .through(text.utf8Encode[IO])
    //     .through(io.file.writeAll[IO](Paths.get(s"/home/peteraa/Fuckton_of_MEA_data/Dummy/$fileName.txt")))

    //   streamLine
    // }

    // memeStream.joinUnbounded.run.unsafeRunSync()
  }
}
