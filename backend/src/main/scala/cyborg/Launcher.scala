package cyborg

import utilz._
import scala.concurrent.ExecutionContext.Implicits.global

object Launcher {
  def main(args: Array[String]): Unit = {

    say("wello")
    Assemblers.startSHODAN.run.unsafeRunSync()
    // scratchpad.doobieChecks()

  }
}
