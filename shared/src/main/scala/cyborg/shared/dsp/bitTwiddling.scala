package cyborg

import com.avsystem.commons.serialization.HasGenCodec

object twiddle {

  trait QueryAble

  // 32 bits of info
  case class Word(w: Int)

  // Information in a Field
  case class Bits(b: Int)

  // An address
  case class Reg(r: Int) extends QueryAble

  // Description of i field
  case class FieldName(n: String) extends QueryAble

  // Description of a value in the context of a field
  case class SettingName(n: String) extends QueryAble

  // The address for a register that should be read
  case class ReadOp(r: Reg)

  object Word extends HasGenCodec[Word]
  object Bits extends HasGenCodec[Bits]
  object Reg extends HasGenCodec[Reg]
  object FieldName extends HasGenCodec[FieldName]
  object SettingName extends HasGenCodec[SettingName]

  // A semantic unit in a register
  case class Field(
    address: Reg,
    first: Int,
    size: Int,
    name: FieldName,
    render: (Field,Bits) => String) extends Ordered[Field] with QueryAble {

    def compare(that: Field): Int = this.first compare that.first

    val last = first + size - 1

    val mask = {
      val dudes = (last - first) + 1
      val bits = (1 << dudes) - 1
      Word(bits << first)
    }

    def ezRender(words: Map[Reg,Word]): Option[String] = {
      words.get(address)
        .map(getFieldValue)
        .map(z => render(this, z))
    }

    def getFieldValue(word: Word): Bits = {
      val rs = word.w << (31 - last)
      val ls = rs >>> (32 - size)
      Bits(ls)
    }

    def asWord(b: Bits): Word = {
      val v = Word(b.b << first)
      v
    }

    def getFieldString(word: Word): String =
      render(this, getFieldValue(word))


    def patch(word: Word, bits: Bits): Word = {
      val bitsToSet = (last - first) + 1
      val mask = (1 << bitsToSet) - 1
      val shiftedMask = mask << first
      val shiftedBits = bits.b << first
      val cleared = ~(~word.w | shiftedMask)
      val set = cleared | shiftedBits

      Word(set)
    }
  }

  object Field extends {
    def generateFields(
      address: Reg,
      first: Int,
      size: Int,
      stride: Int,
      amount: Int,
      baseName: String,
      render: (Field,Bits) => String,
      offset: Int = 0): List[Field] = {

      (0 until amount).map{ i =>
        Field(
          address,
          first + stride*i,
          size,
          FieldName(s"baseName ${i + offset}"),
          render
          )
      }.toList
    }
  }
}
