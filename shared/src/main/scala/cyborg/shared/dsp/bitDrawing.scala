package cyborg

import twiddle._

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
      val bitNumString = (0 until 32).toList.reverse.map(x => "%02d".format(x))

      def getColor(c: Int) =
        if(c % 2 == 0) Console.YELLOW_B else Console.RED_B

      case class Accumulated(bitPositions: List[String], color: Int, pos: Int)
      val renderedPositions = fields.foldLeft(Accumulated(Nil,0,31)){ (x,µ) =>

        val deadDrop = 31 - x.pos
        val deadTake = x.pos - µ.last
        val liveDrop = (31 - x.pos) + (x.pos - µ.last)
        val liveTake = µ.size
        val taken = deadTake + liveTake

        val deadPosString = bitNumString.drop(deadDrop).take(deadTake)
          .map(Console.CYAN + _ + Console.RESET)
        val livePosString = bitNumString.drop(liveDrop).take(liveTake)
          .map(getColor(x.color) + _ + Console.RESET)

        val nextPos = x.pos - taken

        x.copy(
          bitPositions = x.bitPositions ::: deadPosString ::: livePosString,
          color = x.color + 1,
          pos = nextPos
        )
      }

      renderedPositions.copy(
        bitPositions = renderedPositions.bitPositions
          ::: bitNumString.drop(31 - renderedPositions.pos).map(Console.CYAN + _ + Console.RESET),
        ).bitPositions.mkString("|","|","|")
    }


    fields.groupBy(_.address).mapValues(x => renderFieldGroup(x.sorted.reverse))
  }


  def renderFieldWithWords(fields: Map[Reg, List[Field]], reads: Map[Reg, Word]): Map[Reg, String] = {

    def renderFieldGroup(word: Word, fieldz: List[Field]): String = {
      val l = word.w.toBinaryString.toList
      val padLen = 32 - l.size
      val wordString = (List.fill(padLen)('0') ::: l)

      def getColor(c: Int) =
        if(c % 2 == 0) Console.YELLOW else Console.RED

      case class Accumulated(wordString: List[String], color: Int, pos: Int)
      val renderedWord = fieldz.sorted.reverse.foldLeft(Accumulated(Nil,0,31)){ (x,µ) =>

        val deadDrop = 31 - x.pos
        val deadTake = x.pos - µ.last
        val liveDrop = (31 - x.pos) + (x.pos - µ.last)
        val liveTake = µ.size
        val taken = deadTake + liveTake

        val deadWordString = wordString.drop(deadDrop).take(deadTake)
          .map(Console.CYAN + _ + Console.RESET)
        val liveWordString = wordString.drop(liveDrop).take(liveTake)
          .map(getColor(x.color) + _ + Console.RESET)


        val nextPos = x.pos - taken

        x.copy(
          wordString = x.wordString ::: deadWordString ::: liveWordString,
          color = x.color + 1,
          pos = nextPos
        )
      }

      val settings = fieldz.reverse.foldLeft((0,""))( (x,µ) => ((x._1 + 1), (x._2 + getColor(x._1) + s"${µ.name.n} <- ${µ.getFieldString(word)}\n")))._2
      val frame = List.fill(32)("").mkString("+--","+--","+\n")

      val done = renderedWord.copy(
        wordString = renderedWord.wordString
          ::: wordString.drop(31 - renderedWord.pos).map(Console.CYAN + _ + Console.RESET)
      ).wordString.mkString("| ","| ","|")

      "\n" + frame + done + "\n" + frame + settings

    }

    val merged = bonus.intersect(fields, reads)

    merged.mapValues(x => renderFieldGroup(x._2, x._1))
  }
}
