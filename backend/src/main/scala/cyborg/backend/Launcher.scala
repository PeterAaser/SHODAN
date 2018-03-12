package cyborg.backend

import cyborg.backend.server.ApplicationServer
import io.udash.logging.CrossLogging
import scala.concurrent.ExecutionContext.Implicits.global

import scala.io.StdIn
import cyborg._
import utilz._

object Launcher extends CrossLogging {
  def main(args: Array[String]): Unit = {

    say("wello")

    params.printParams()
    Assemblers.startSHODAN.compile.drain.unsafeRunSync()

    // val startTime = System.nanoTime
    // val server = new ApplicationServer(
    //   8080,
    //   "frontend/target/UdashStatics/WebContent"
    // )
    // server.start()
    // import scala.concurrent.duration._
    // val duration: Double = (System.nanoTime - startTime).nanos.toUnit(SECONDS)
    // logger.info(s"Application started in ${duration}s.")


    // val startTime = System.nanoTime

    // val ctx = SpringContext.createApplicationContext("beans.conf")
    // val server = ctx.getBean(classOf[ApplicationServer])
    // server.start()

    // val duration: Long = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime - startTime)
    // logger.info(s"Application started in ${duration}s.")

    // // wait for user input and then stop the server
    // logger.info("Click `Enter` to close application...")
    // StdIn.readLine()
    // server.stop()
  }
}
