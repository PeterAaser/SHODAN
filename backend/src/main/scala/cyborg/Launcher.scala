package cyborg

import utilz._
import scala.concurrent.ExecutionContext.Implicits.global

object Launcher {
  def main(args: Array[String]): Unit = {

    say("wello")

    params.printParams()
    Assemblers.startSHODAN.compile.drain.unsafeRunSync()
  }
}
