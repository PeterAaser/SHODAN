package cyborg

import cats.effect.IO
import twiddle._
import STG._
import DspRegisters._
import utilz._
import shapeless._
import BitDrawing._

object DspRoutines {

  val frame = List.fill(32)("").mkString("+--","+--","+\n")

  case class RegistryGroup(
    name: String,
    fields: List[Field],

    // for instance get(0) => Some("Trigger 1")
    valueToSettings: Map[Bits, SettingName],
    description: String
  ){

    val settingsToValue:  Map[SettingName, Bits] = valueToSettings.map(_.swap)
    val fieldNameToField: Map[FieldName, Field]  = fields.sorted.map(λ => (λ.name, λ)).toMap
    val regToFields:      Map[Reg, List[Field]]  = fields.sorted.groupBy(λ => λ.address)

    val renderSettings: String =
      valueToSettings.toList.map(λ => s"${λ._2} -> ${asNdigitBinary(λ._1.b, fields.head.size)}").mkString("\n")

    def writeSettingToField(s: SettingName, f: FieldName): WriteOp =
      WriteOp(fieldNameToField.get(f).get, settingsToValue.get(s).get)

    def writeValueToField(b: Bits, f: FieldName): WriteOp =
      WriteOp(fieldNameToField.get(f).get, b)

    // renders the fields register manifest
    def fieldsToString: Map[Reg, String] = BitDrawing.renderFields(fields)

    def wordsToString(words: Map[Reg, Word]): Map[Reg, String] =
      BitDrawing.renderFieldWithWords(regToFields, words)


    def writeValues(v: Map[Int, String]): List[WriteOp] = {
      val tmp = v.toList
      val bits = tmp.map(a => Bits(a._1))
      val fields = tmp.map(a => FieldName(a._2))

      WriteOp.coalesceOps((bits zip fields).map(Function.tupled(writeValueToField)))
    }

    def writeSettings(s: List[(String, String)]): List[WriteOp] = {
      val tmp = s.toList
      val settings = tmp.map(a => SettingName(a._1))
      val fields = tmp.map(a => FieldName(a._2))
      val dudes = WriteOp.coalesceOps((settings zip fields).map(Function.tupled(writeSettingToField)))

      dudes
    }



    def renderFields: String = {
      Console.RED + name + Console.RESET + "\n" + description + "\n" + renderSettings +
      fieldsToString.toList.map{ case (reg: Reg, fieldString: String) =>
        s"\nat address 0x${reg.r.toHexString}\n" +
          frame + fieldString + "\n" + frame
      }.mkString("\n")
    }

    def renderWords(reads: Map[Reg,Word]): String = {
      val intro = Console.RED + name + Console.RESET + "\n" + description + "\n" + renderSettings + "\n"
      val memes = renderFieldWithWords(regToFields, reads)
      val merged = intersect(fieldsToString, memes)

      intro + merged.toList.map{ case(λ,µ) =>
        frame + µ._1 + µ._2
      }.mkString("\n")
    }
  }

}
