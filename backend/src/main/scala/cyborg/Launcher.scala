package cyborg

import cats.effect.IO
import java.nio.file.Paths
import scala.concurrent.ExecutionContext.Implicits.global

object Launcher {
  def main(args: Array[String]): Unit = {

    println("wello")

    import fs2.Stream._
    import fs2.Stream

    import scala.concurrent.duration._
    import spire.syntax.literals.radix._

    import DspRoutines._
    import twiddle._

    // mainRunner.httpRunner.unsafeRun()
    Assemblers.startSHODAN.run.unsafeRunSync()

    // val hurp = STG.TriggerSelectBF.writeSettingToField(SettingName("Trigger 1"), FieldName("Mem 1"))
    // println(STG.TriggerSelectBF.renderFields)

    // val testReads = Map(
    //   Reg(0x9104) -> Word(0xFF00),
    //   Reg(0x9108) -> Word(0x00FF)
    // )

    // println(STG.TriggerSelectBF.writeValues(
    //   Map(x2"10" -> "Mem 7",
    //       x2"01" -> "Mem 5"))
    // )

    // val writes = STG.TriggerSelectBF.writeSettings(
    //   List("Trigger 3" -> "Mem 7",
    //        "Trigger 3" -> "Mem 5",
    //        "Trigger 3" -> "Mem 3",
    //        "Trigger 3" -> "Mem 1",
    //        "Trigger 2" -> "Mem 2",
    //        "Trigger 2" -> "Mem 4",
    //        "Trigger 2" -> "Mem 6",
    //        "Trigger 2" -> "Mem 8")
    // ).map(λ => (λ.r, λ.execute(Word(0)))).toMap

    // println(STG.TriggerSelectBF.renderWords(writes))
    // println(STG.theWholeFuckingLot.map(_.renderFields).mkString("\n\n"))


    import cats.syntax._
    import cats._
    import cats.Applicative
    import cats.instances._
    import cats.implicits._

    // val testme = STG.theWholeFuckingLot.map(λ => renderRegisterFromDSP(λ)).sequence.map(_.mkString("\n\n")).map(print(_))
    // (Stream.eval(HttpClient.dspConnect) >> Stream.eval(HttpClient.dspTest) >>
    // Stream.eval(testme)).run.unsafeRunSync()

    // println(STG.TriggerSelectBF.renderWords(testReads))
    // println(STG.TriggerSelectBF.renderFields)
    // println(STG.ElectrodeModeBF.renderFields)

    println("OK")
    println(Console.GREEN + Console.BOLD + "✓")
    println(Console.RED + Console.BOLD + "✘")

  }
}
