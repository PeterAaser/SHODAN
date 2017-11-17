package cyborg

/**
  Operations for treating subfields of registers as semantic values, allowing them to
  set, read and represented in an understandable manner.
  */
object twiddle {

  import utilz.asNdigitBinary

  type Register = Int // An address
  type DSPWord  = Int // A 32 bit word
  type DSPField = Int // A subfield

  val frame = (0 to 32).map(_ => "").toList.mkString("","+--","+\n")

  /**
    A bitfield signifies a subfield of a register. For instance the first 2 bits of a register.
    When setting a bitfield we must ensure that we do not overwrite the previous value in the
    parts of the register outside our bitfield. This operation is handled on the DSP, but we
    need to tag it as such, hence the extra representation

    Note that a BitField does not carry any state, they're simply descriptors of a register.
    Data should preferably not be stored on SHODAN unless absolutely necessary.

    The read method is DSPWord => Int, this means you must supply the DSPWord!
    */
  case class BitField(
    address: Int,
    description: String,
    nBits: Int,
    start: Int,
    registerMap: Option[Map[Int,String]]
  ){

    def read(register: DSPWord): DSPField = {
      val ls = register << start
      ls >>> (32 - nBits)
    }


    def translate(b: BitField) = b.registerMap.map { λ =>
        λ.foldLeft("")( (acc: String, pair: (Int, String)) =>
          acc + s"${asNdigitBinary(pair._1, b.nBits)} --> ${pair._2}\n")
    }.getOrElse("")


    def getLayout: List[Boolean] =
      (0 until 32).map(λ => (!(λ > start) && (!(λ < (1 + start - nBits))))).reverse.toList


    override def toString(): String = {

      val basic = "bit field at address " + address.toHexString +
        "\n" + s"description: $description\n"

      val fill = getLayout.zipWithIndex.map{λ =>
        if(!λ._1) "|" + Console.CYAN + "%02d".format((32 - λ._2)-1) + Console.RESET else "|" + Console.YELLOW_B + "%02d".format((32 - λ._2)-1) + Console.RESET
      }.foldLeft("")(_+_) + "|\n"

      basic + frame + fill + frame
    }

    def getValue(r: DSPWord): String = {
      val dur = registerMap.flatMap(_.get(read(r))).getOrElse(asNdigitBinary(read(r), nBits))
      description + " <- " + dur
    }
  }


  /**
    A logical grouping of bitfields
    */
  case class BitFieldGroup(
    description: String,
    members: List[BitField],
    longDescription: String = ""
  ){
    def showRegister(r: DSPWord): String = {

      val layouts = (List.fill(32)(false) /: members.map(_.getLayout))( (acc, λ) =>
        {
          (acc zip λ).map(λ => λ._1 || λ._2)
        })

      var color = 0
      def getColor(): String = {
        color = color + 1
        if(((color-1)/members.head.nBits) % 2 == 0)
          Console.YELLOW_B
        else
          Console.RED_B
      }
      val fill = layouts.zipWithIndex.map{λ =>
        if(!λ._1) "|" + Console.CYAN + "%02d".format((32 - λ._2)-1) + Console.RESET else "|" + getColor() + "%02d".format((32 - λ._2)-1) + Console.RESET
      }.foldLeft("")(_+_) + "|\n"

      val regString = utilz.asNdigitBinaryPretty(r, 32, layouts, members.head.nBits)
      val valString = members.foldLeft(""){ (λ, µ) =>
        λ + µ.getValue(r) + "\n"
      }

      "\n" + Console.BOLD + Console.YELLOW + description + Console.RESET + longDescription + frame + fill + frame + regString + frame + valString
    }
  }


  /**
    Generate many bitfields at the same address. Note that due to endian-ness start and end
    might be a little different logically
    */
  def generateBitFields(
    address: Int,
    description: String,
    stride: Int,
    start: Int,
    end: Int,
    registerMap: Option[Map[Int,String]],
    offset: Int = 0
  ): Vector[BitField] = {

    val numRegisters = (start - end)/stride
    (0 until numRegisters).map { regNo =>
      val bfDescription = description + s" ${regNo + offset}"
      val regStart = start + (regNo*stride)
      BitField(address, bfDescription, stride, regStart, registerMap)
    }.toVector
  }

}
