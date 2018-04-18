package cyborg.backend

import scala.concurrent.ExecutionContext.Implicits.global

import cyborg._
import utilz._

object Launcher {
  def main(args: Array[String]): Unit = {

    say("wello")

    Assemblers.startSHODAN.compile.drain.unsafeRunSync()
  }
}
