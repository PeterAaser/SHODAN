package cyborg


import cats.effect.IO
import cats.effect._


import HttpClient._
import DspRegisters._
import scala.util.Random

object DspComms {

  implicit class regSetInfix(val reg: Int){ def :=(that: Int): (Int,Int) = (this.reg, that) }

  val hurr = DEBUG1 := 0x1234
  val durr = hurr._1

  def pingTest(): IO[Unit] = {
    val rnd = new Random()
    val addressList = List(DEBUG1,DEBUG2,DEBUG3,DEBUG4)
    val testVals = List.fill(4)(0x1000 + rnd.nextInt(100))
    val regSet = RegisterSetList(addressList, testVals)
    val regRead = RegisterReadList(addressList)

    for {
      write <- setRegistersRequest(regSet)
      read  <- readRegistersRequest(regRead)
    } yield {
      if(read.values.zip(testVals).map(λ => (λ._1 == λ._2)).foldLeft(true)(_&&_)){
        println(Console.GREEN + "Ping test OK" + Console.RESET)
      }
      else{
        println(Console.GREEN + "Ping test failed!!" + Console.RESET)
      }
    }
  }


  /**
    Tests the DSP read functionality.
    DEBUG5       <- magicNumber
    READ_ADDRESS <- DEBUG5
    READ_REQ     <- magicIdNumber

    should yield

    READ_ACK   == magicIdNumber
    READ_VALUE == magicNumber
    */
  def readTest(): IO[Unit] = {
    val rnd = new Random()
    val magicNumber = rnd.nextInt(100)+ 0x3000
    val magicIdNumber = rnd.nextInt(100)+ 0x4000

    val regWrite_ = List(
      magicNumber   -> DEBUG5,
      DEBUG5        -> READ_ADDRESS,
      magicIdNumber -> READ_REQ_ID)


    val regRead_ = List(
      READ_ACK_ID,
      READ_VALUE)

    val regWrite = RegisterSetList(regWrite_)
    val regRead = RegisterReadList(regRead_)

    for {
      write <- setRegistersRequest(regWrite)
      read  <- readRegistersRequest(regRead)
    } yield {
      if((magicIdNumber == read.values(0) && (magicNumber == read.values(1)))){
        println(Console.GREEN + "read test OK" + Console.RESET)
      }
      else{
        println(Console.RED + "read test failed!!" + Console.YELLOW)
        println(f"magic number: $magicNumber%x")
        println(f"magic id number: $magicIdNumber%x")

        regWrite.show()
        read.show(regRead)

        println(Console.RESET + "-----------------\n\n")

      }
    }
  }
  def writeTest(): IO[Unit] = {
    val rnd = new Random()
    val magicNumber = rnd.nextInt(100)+ 0x5000
    val magicIdNumber = rnd.nextInt(100)+ 0x6000

    val regWrite_ = List(
      DEBUG6 -> WRITE_ADDRESS,
      magicNumber -> WRITE_VALUE,
      magicIdNumber -> WRITE_REQ_ID)

    val regRead_ = List(WRITE_ACK_ID, DEBUG6)


    val regWrite = RegisterSetList(regWrite_)
    val regRead = RegisterReadList(regRead_)

    for {
      write <- setRegistersRequest(regWrite)
      read  <- readRegistersRequest(regRead)
    } yield {
      if((magicIdNumber == read.values.head) && (magicNumber == read.values.last)){
        println(Console.GREEN + "write test OK" + Console.RESET)
      }
      else{
        println(Console.RED + "write test failed!!" + Console.RESET)
        println(f"magic number: $magicNumber%x")
        println(f"magic id number: $magicIdNumber%x")

        regWrite.show()
        read.show(regRead)

        println(Console.RESET + "-----------------\n\n")
      }
    }
  }
  def getDebug(): IO[Unit] = {
    val regRead_ = List(DEBUG1,DEBUG2,DEBUG3,DEBUG4,DEBUG5,DEBUG6,DEBUG7,DEBUG8,DEBUG9)
    val regRead = RegisterReadList(regRead_)

    for {
      read  <- readRegistersRequest(regRead)
    } yield {
      println("debug registers:")
      read.show(regRead)
    }
  }


  def clearDebug(): IO[Unit] = {

    val regWrite_ = List(
      0 -> CLEAR)

    val regWrite = RegisterSetList(regWrite_)

    for {
      read  <- setRegistersRequest(regWrite)
    } yield {
      println("debug registers cleared")
      println("WARNING: CURRENTLY DISABLED ON MEAME")
    }
  }


  def stimuliRequest(vision: List[Double]): IO[Unit] = {

    import MEAMEutilz._
    def toStimFrequency(transform: Double => Double, distance: Double): Double = {
      setDomain(transform)(distance)
    }

    val desc2 = vision.map(toStimFrequency( logScaleBuilder(scala.math.E), _)).foldLeft(0.0)(_+_)

    val period = if(desc2 > 0) 10000 else 100000

    for {
      write <- simpleStimRequest(SimpleStimReq(period))
    } yield {
      println(s"sent stim req $period")
    }
  }

  import fs2._
  def stimuliRequestSink(throttle: Int = 1000): Sink[IO,List[Double]] = {
    _.through(utilz.mapN(throttle, _.toArray.head)).evalMap(stimuliRequest)
  }


  /**
    modifies the bits of a single register
    */
  def setBitField_(reg: Int, bits: Int, firstBit: Int, lastBit: Int, printMe: String = "no", printBits: Int = 8): Int = {

    import utilz.asNdigitBinary

    val bitsToSet = (lastBit - firstBit + 1)
    val mask = (1 << bitsToSet) - 1
    val shiftedMask = mask << firstBit
    val shiftedBits = bits << firstBit
    val cleared = ~(~reg | shiftedMask)
    val set = cleared | shiftedBits

    if(printMe == "print me"){

      val offset = (printBits - lastBit)
      val offsets = ("" /: (0 until offset - 1))((a,b) => a ++ " ")
      val points = ("" /: (0 until (lastBit - firstBit + 1)))((a,b) => a ++ "^")
      println(s"setting bits ${asNdigitBinary(bits, (lastBit - firstBit + 1))} " ++
                s"from positions $lastBit to $firstBit\n")

      println(s"init:        ${asNdigitBinary(reg, printBits)}")
      println(s"set:         ${offsets}${asNdigitBinary(bits, (lastBit - firstBit +1))}")
      println(s"to:          ${asNdigitBinary(set, printBits)}")
      println(s"             ${offsets}${points}")
      println()
      println(s"result in decimal: ${set}")
    }

    set
  }

  /**
    Sets a bitfield with given value
    */
  def setBitField(b: BitField, reg: Int, bits: Int, printMe: String = "no", printBits: Int = 32): Int = {
    println(b)

    val legalValue = b.translation.map(_.contains(bits)).getOrElse(true)
    val legalLength = ((1 << b.nBits) - 1) > bits
    val isLegal = legalValue && legalLength

    if(isLegal){
      setBitField_(reg, bits, b.start - b.nBits, b.start, printMe, printBits)
    }
    else if(legalLength){
      println("Illegal value")
      setBitField_(reg, bits, b.start - b.nBits, b.start, printMe, printBits)
    }
    else{
      println("Illegal length")
      0
    }
  }


  def readBitFields(bs: Set[BitField]): IO[RegisterReadResponse] = {
    val addressList = bs.map(_.address).toSet.toList
    HttpClient.readRegistersRequest(RegisterReadList(addressList))
  }


  def writeBitFields(bs: List[(BitField, Int)]): IO[String] = {
    val addresses = bs.map(_._1.address).toSet.toList
    val reads = readBitFields(bs.map(_._1).toSet)
    val partitionedByAddress = bs.groupBy(_._1.address)
    val collapsed = partitionedByAddress.map{ a =>
      val address = a._1
      val writeVals: List[(BitField, Int)] = a._2
      (address, (µ: Int) => writeVals.foldLeft(µ)( (reg, λ) => setBitField(λ._1, reg, λ._2)))
    }

    println(collapsed)
    println(addresses)

    reads flatMap{ response =>
      val registers = (response.values zip addresses)
      val writes = RegisterSetList(registers.map(λ => (λ._2, collapsed.get(λ._2).get(λ._1))))
      HttpClient.setRegistersRequest(writes)
    }
  }
}
