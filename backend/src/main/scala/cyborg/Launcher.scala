package cyborg

import scala.concurrent.ExecutionContext.Implicits.global

object Launcher {
  def main(args: Array[String]): Unit = {

    println("wello")

    // params.printParams()
    // Assemblers.startSHODAN.run.unsafeRunSync()
    // Assemblers.assembleMcsFileReader.run.unsafeRunSync()

    import fs2.Stream._
    import fs2.Stream

    import scala.concurrent.duration._
    simpleFeedback.doIt.flatMap(Stream.eval(_)).run.unsafeRunTimed(1.second)

    println("OK")
  }
}
