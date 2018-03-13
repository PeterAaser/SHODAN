package cyborg.backend

import io.udash.logging.CrossLogging
import scala.concurrent.ExecutionContext.Implicits.global

import cyborg._
import utilz._

object Launcher extends CrossLogging {
  def main(args: Array[String]): Unit = {

    say("wello")

    params.printParams()
    Assemblers.startSHODAN.compile.drain.unsafeRunSync()
  }
}
