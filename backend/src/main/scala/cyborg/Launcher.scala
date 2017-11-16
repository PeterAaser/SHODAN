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

    //         7      0
    val a = x2"00001111"
    val b = x2"10"
    val d = x2"00001101"
    val e = x2"01001111"



    println(s"d: $d")
    println(s"e: $e")

    val test1 = DspComms.setBitField_(a, b, 1, 2, "print me")
    println(s"test1: $test1")

    println()

    val test2 = DspComms.setBitField_(a, b, 5, 6, "print me")
    println(s"test2: $test2")


    val trips1 = x2"101"
    val trips2 = x2"010"

    println()
    val test3 = DspComms.setBitField_(a, trips1, 1, 3, "print me", 32)
    println()
    val test4 = DspComms.setBitField_(a, trips1, 5, 7, "print me", 32)
    println()

    println("OK")

    DspComms.setBitField(STG.TriggerSelect.head, 0, x2"01", "print me", 32)
  }
}
