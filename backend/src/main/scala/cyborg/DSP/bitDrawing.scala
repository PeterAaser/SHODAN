package cyborg

import twiddle._
import utilz._

object BitDrawing {


  def asNdigitBinary (source: Int, digits: Int): String = {
    val l  = source.toBinaryString
    val padLen = digits - l.size
    val pad = ("" /: (0 until padLen).map(_ => "0"))(_+_)
    pad + l
  }
  def as32BinarySpaced (source: Int): String = {
    val l  = source.toBinaryString
    val padLen = 32 - l.size
    val pad = ("" /: (0 until padLen).map(_ => "0"))(_+_)
    val s = pad + l
    s.take(8) + " " + s.drop(8).take(8) + " " + s.drop(16).take(8) + " " + s.drop(24).take(8)
  }
  def asHex(f: Field, b: Bits): String = "0x" + b.b.toHexString
  def asBin(f: Field, b: Bits): String = {
    "0b" + asNdigitBinary(b.b, f.size)
  }


  def renderFields(fields: List[Field]): Map[Reg, String] = {

    def renderFieldGroup(fields: List[Field]): String = {
      val bitNumString = (0 until 32).toList.reverse.map(λ => "%02d".format(λ))

      def getColor(c: Int) =
        if(c % 2 == 0) Console.YELLOW_B else Console.RED_B

      case class Accumulated(bitPositions: List[String], color: Int, pos: Int)
      val renderedPositions = fields.foldLeft(Accumulated(Nil,0,31)){ (λ,µ) =>

        val deadDrop = 31 - λ.pos
        val deadTake = λ.pos - µ.last
        val liveDrop = (31 - λ.pos) + (λ.pos - µ.last)
        val liveTake = µ.size
        val taken = deadTake + liveTake

        val deadPosString = bitNumString.drop(deadDrop).take(deadTake)
          .map(Console.CYAN + _ + Console.RESET)
        val livePosString = bitNumString.drop(liveDrop).take(liveTake)
          .map(getColor(λ.color) + _ + Console.RESET)

        val nextPos = λ.pos - taken

        λ.copy(
          bitPositions = λ.bitPositions ::: deadPosString ::: livePosString,
          color = λ.color + 1,
          pos = nextPos
        )
      }

      renderedPositions.copy(
        bitPositions = renderedPositions.bitPositions
          ::: bitNumString.drop(31 - renderedPositions.pos).map(Console.CYAN + _ + Console.RESET),
        ).bitPositions.mkString("|","|","|")
    }


    fields.groupBy(_.address).mapValues(λ => renderFieldGroup(λ.sorted.reverse))
  }


  def renderFieldWithWords(fields: Map[Reg, List[Field]], reads: Map[Reg, Word]): Map[Reg, String] = {

    def renderFieldGroup(word: Word, fieldz: List[Field]): String = {
      val l = word.w.toBinaryString.toList
      val padLen = 32 - l.size
      val wordString = (List.fill(padLen)('0') ::: l)

      def getColor(c: Int) =
        if(c % 2 == 0) Console.YELLOW else Console.RED

      case class Accumulated(wordString: List[String], color: Int, pos: Int)
      val renderedWord = fieldz.sorted.reverse.foldLeft(Accumulated(Nil,0,31)){ (λ,µ) =>

        val deadDrop = 31 - λ.pos
        val deadTake = λ.pos - µ.last
        val liveDrop = (31 - λ.pos) + (λ.pos - µ.last)
        val liveTake = µ.size
        val taken = deadTake + liveTake

        val deadWordString = wordString.drop(deadDrop).take(deadTake)
          .map(Console.CYAN + _ + Console.RESET)
        val liveWordString = wordString.drop(liveDrop).take(liveTake)
          .map(getColor(λ.color) + _ + Console.RESET)


        val nextPos = λ.pos - taken

        λ.copy(
          wordString = λ.wordString ::: deadWordString ::: liveWordString,
          color = λ.color + 1,
          pos = nextPos
        )
      }

      val settings = fieldz.reverse.foldLeft((0,""))( (λ,µ) => ((λ._1 + 1), (λ._2 + getColor(λ._1) + s"${µ.name.n} <- ${µ.getFieldString(word)}\n")))._2
      val frame = List.fill(32)("").mkString("+--","+--","+\n")

      val done = renderedWord.copy(
        wordString = renderedWord.wordString
          ::: wordString.drop(31 - renderedWord.pos).map(Console.CYAN + _ + Console.RESET)
      ).wordString.mkString("| ","| ","|")

      "\n" + frame + done + "\n" + frame + settings

    }

    val merged = utilz.intersect(fields, reads)

    merged.mapValues(λ => renderFieldGroup(λ._2, λ._1))
  }
}
