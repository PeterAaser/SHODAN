package com.cyborg

import com.github.nscala_time.time.Imports._

import fs2.util.Async
import fs2.{io, text, Task}
import java.nio.file.Paths
import fs2.io.tcp._
import fs2._

object FW {

  // def fahrenheitToCelsius(f: Double): Double =
  //   (f - 32.0) * (5.0/9.0)

  // val converter: Task[Unit] =
  //   io.file.readAll[Task](Paths.get("testdata/fahrenheit.txt"), 4096)
  //     .through(text.utf8Decode)
  //     .through(text.lines)
  //     .filter(s => !s.trim.isEmpty && !s.startsWith("//"))
  //     .map(line => fahrenheitToCelsius(line.toDouble).toString)
  //     .intersperse("\n")
  //     .through(text.utf8Encode)
  //     .through(io.file.writeAll(Paths.get("testdata/celsius.txt")))
  //     .run

  // val u: Unit = converter.unsafeRun()


  def meameWriter[F[_]: Async](meameSocket: Socket[F]): Stream[F, Unit] = {

    // val paramString = "This recording was done with these and these params\n".toByte
    // val fileName = s"recording ${DateTime.now().toString}"

    val reads: Stream[F, Byte] = meameSocket.reads(1024*1024)

    val thing = reads
      .through(io.file.writeAll(Paths.get("/home/peter/MEAMEdata/test123")))

    thing
  }
}
