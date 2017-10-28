package cyborg

import scala.concurrent.ExecutionContext.Implicits.global

object Launcher {
  def main(args: Array[String]): Unit = {

    println("wello")

    // params.printParams()
    Assemblers.startSHODAN.run.unsafeRunSync()

  }
}
