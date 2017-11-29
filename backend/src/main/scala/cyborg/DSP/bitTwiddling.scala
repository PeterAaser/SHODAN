package cyborg

import scala.math.Ordered.orderingToOrdered

object twiddle {

  case class Word(w: Int) extends AnyVal
  case class Bits(b: Int) extends AnyVal
  case class Reg(r: Int) extends AnyVal
  case class FieldName(n: String) extends AnyVal
  case class SettingName(n: String) extends AnyVal


  // No checks are done that writeop reg matches
  case class WriteOp(r: Reg, word: Word, mask: Word){
    def coalesce(that: WriteOp): WriteOp =
      copy(word = Word(word.w | that.word.w), mask = Word(mask.w | that.mask.w))

    def execute(w: Word): Word = {
      val masked = w.w & ~mask.w
      Word(masked | word.w)
    }
  }
  case object WriteOp {
    def apply(f: Field, b: Bits): WriteOp = {
      WriteOp(f.address, f.asWord(b), f.mask)
    }

    def coalesceOps(m: List[WriteOp]): List[WriteOp] = {
      m.groupBy(_.r).mapValues(_.reduce(_.coalesce(_))).toList.map(_._2)
    }
  }

  case class ReadOp(r: Reg)

  case class Field(
    address: Reg,
    first: Int,
    size: Int,
    name: FieldName,
    render: (Field, Bits) => String) extends Ordered[Field] {

    def compare(that: Field): Int = this.first compare that.first

    val last = first + size - 1

    val mask = {
      val dudes = (last - first) + 1
      val bits = (1 << dudes) - 1
      Word(bits << first)
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
  object Field {
    def generateFields(
      address: Reg,
      first: Int,
      size: Int,
      stride: Int,
      amount: Int,
      baseName: String,
      render: (Field, Bits) => String,
      offset: Int = 0): List[Field] = {

      (0 until amount).map{ i =>
        Field(
          address,
          first + stride*i,
          size,
          FieldName(s"baseName ${i + offset}"),
          render)
      }.toList
    }
  }
}
