package cyborg

import utilz._
import scala.concurrent.ExecutionContext.Implicits.global

object Launcher {
  def main(args: Array[String]): Unit = {

    say("wello")

    say(params.filtering.spikeCooldown)
    // Assemblers.startSHODAN.compile.drain.unsafeRunSync()
    scratchpad.doiIt
    // simpleFeedback.doIt.evalMap(identity).compile.drain.unsafeRunSync()
  }
}
