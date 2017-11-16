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
    // simpleFeedback.doIt.flatMap(Stream.eval(_)).run.unsafeRunTimed(1.second)

    import spire.syntax.literals.radix._

    println("OK")

    val setlist = STG.TriggerSelect.zip(List(3,3,3,3,3,2,1,0))

    val huh = Stream.eval(HttpClient.dspConnect) >>
      Stream.eval(HttpClient.dspTest) >>
      Stream.eval(DspComms.writeBitFields(setlist))

    huh.run.unsafeRunSync()

    // DspComms.setBitField(STG.TriggerSelect.head, 0, x2"01", "print me", 32)
  }
}
