package cyborg

import scala.math.Ordered.orderingToOrdered

object twiddle {

  case class Word(w: Int) extends AnyVal
  case class Bits(b: Int) extends AnyVal
  case class Reg(r: Int) extends AnyVal
  case class FieldName(n: String) extends AnyVal


  case class Field(first: Int, size: Int, name: String, render: (Field, Bits) => String) extends Ordered[Field] {
    def compare(that: Field): Int = this.first compare that.first

    val last = first + size - 1

    def getFieldValue(word: Word): Bits = {
      val rs = word.w << (31 - last)
      val ls = rs >>> (32 - size)
      Bits(ls)
    }

    def getFieldString(word: Word): String =
      render(this, getFieldValue(word))

    def patch(word: Word, bits: Bits): Word = {
      val bitsToSet = (last - first)
      val mask = (1 << bitsToSet) - 1
      val shiftedMask = mask << first
      val shiftedBits = bits.b << first
      val cleared = ~(~word.w | shiftedMask)
      val set = cleared | shiftedBits

      Word(set)
    }
  }


  def asNdigitBinary (source: Int, digits: Int): String = {
    val l  = source.toBinaryString
    val padLen = digits - l.size
    val pad = ("" /: (0 until padLen).map(_ => "0"))(_+_)
    pad + l
  }
  def asHex(f: Field, b: Bits): String = "0x" + b.b.toHexString
  def asBin(f: Field, b: Bits): String = {
    "0b" + asNdigitBinary(b.b, f.size)
  }

  def showRegister(word: Word, fields: List[Field]): String = {
    val l = word.w.toBinaryString.toList
    val padLen = 32 - l.size
    val padded = (List.fill(padLen)('0') ::: l)
    val posString = (0 until 32).toList.reverse.map(λ => "%02d".format(λ))

    def getColor(c: Int) =
      if(c % 2 == 0) Console.YELLOW else Console.RED
    def getColor_B(c: Int) =
      if(c % 2 == 0) Console.YELLOW_B else Console.RED_B

    case class Accumulated(fill: List[String], bits: List[String], color: Int, pos: Int)
    val coloredStrings_ = fields.sorted.reverse.foldLeft(Accumulated(Nil,Nil,0,31)){ (λ,µ) =>

      val deadDrop = 31 - λ.pos
      val deadTake = λ.pos - µ.last
      val liveDrop = (31 - λ.pos) + (λ.pos - µ.last)
      val liveTake = µ.size
      val taken = deadTake + liveTake

      val deadBitString = padded.drop(deadDrop).take(deadTake)
        .map(Console.CYAN + _ + Console.RESET)
      val liveBitString = padded.drop(liveDrop).take(liveTake)
        .map(getColor(λ.color) + _ + Console.RESET)

      val deadPosString = posString.drop(deadDrop).take(deadTake)
        .map(Console.CYAN + _ + Console.RESET)
      val livePosString = posString.drop(liveDrop).take(liveTake)
        .map(getColor_B(λ.color) + _ + Console.RESET)

      val nextPos = λ.pos - taken

      λ.copy(
        fill = λ.fill ::: deadBitString ::: liveBitString,
        bits = λ.bits ::: deadPosString ::: livePosString,
        color = λ.color + 1,
        pos = nextPos
      )
    }

    val coloredStrings = coloredStrings_.copy(
      fill = coloredStrings_.fill ::: padded.drop(31 - coloredStrings_.pos).map(Console.CYAN + _ + Console.RESET),
      bits = coloredStrings_.bits ::: posString.drop(31 - coloredStrings_.pos).map(Console.CYAN + _ + Console.RESET),
    )

    val fillString = coloredStrings.fill.mkString("| ","| ","|")
    val bitString = coloredStrings.bits.mkString("|","|","|")

    val frame = List.fill(32)("").mkString("+--","+--","+")

    frame + "\n" +
      bitString + "\n" +
      frame + "\n" +
      fillString + "\n" +
      frame + "\n" +
      fields.foldLeft("")( (λ,µ) => s"$λ\n${µ.name} <- ${µ.getFieldString(word)}")
  }

  def dothing = {
    val fields = List(
      Field(0, 2, "a",  asBin),
      Field(2, 2, "a",  asBin),
      Field(5, 2, "b",  asBin),
      Field(8, 2, "c",  asBin),
      Field(11, 2, "d", asBin)
    ).reverse

    import spire.syntax.literals.radix._
    val theWord = Word(x2"01010101111111110000000111001010")

    println(showRegister(theWord, fields))
  }
}
