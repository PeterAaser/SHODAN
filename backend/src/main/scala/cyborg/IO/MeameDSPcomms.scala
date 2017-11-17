package cyborg


import cats.effect.IO
import cats.effect._


import HttpClient._
import DspRegisters._
import scala.util.Random
import twiddle._

object DspComms {

  import twiddle._

  implicit class regSetInfix(val reg: Register){ def :=(that: DSPWord): (Register,DSPWord) = (this.reg, that) }

  import DspRegisters._

  case class RegisterSetList(addresses: List[Register], values: List[DSPWord])
  case class RegisterReadList(addresses: List[Register])
  case class RegisterReadResponse(addresses: List[Register], values: List[DSPWord])

  case object RegisterSetList {
    def apply(r: List[(Register,DSPWord)]): RegisterSetList = RegisterSetList(r.unzip._2, r.unzip._1)
  }



  import fs2._
  def stimuliRequestSink(throttle: Int = 1000): Sink[IO,List[Double]] = {

    def stimuliRequest(vision: List[Double]): IO[Unit] = {

      import MEAMEutilz._
      def toStimFrequency(transform: Double => Double, distance: Double): Double = {
        setDomain(transform)(distance)
      }

      // placeholder
      val desc2 = vision.map(toStimFrequency( logScaleBuilder(scala.math.E), _)).foldLeft(0.0)(_+_)

      val period = if(desc2 > 0) 10000 else 100000

      for {
        write <- simpleStimRequest(SimpleStimReq(period))
      } yield {
        println(s"sent stim req $period")
      }
    }
    _.through(utilz.mapN(throttle, _.toArray.head)).evalMap(stimuliRequest)
  }


  /**
    Coalesces a bunch of bit field reads, avoiding superflous reads.
    */
  def readBitFields(bs: Set[BitField]): IO[RegisterReadResponse] = {
    val addressList = bs.map(_.address).toSet.toList
    HttpClient.readRegistersRequest(RegisterReadList(addressList))
  }


  /**
    Coalesces a bunch of bit field set operations, avoiding both
    superflous reads and writes.
    */
  def writeBitFields(bs: List[(BitField, DSPField)]): IO[String] = {
    val reads: IO[RegisterReadResponse] = readBitFields(bs.map(_._1).toSet)
    val partitionedByAddress = bs.groupBy(_._1.address)
    val collapsed = partitionedByAddress.map{ a =>
      val address = a._1
      val writeVals: List[(BitField, DSPField)] = a._2
      (address, (µ: DSPWord) => writeVals.foldLeft(µ)( (reg, λ) => setBitField(λ._1, reg, λ._2)))
    }

    reads flatMap{ response =>
      val registers = (response.values zip response.addresses)
      val writes = RegisterSetList(registers.map(λ => (λ._2, collapsed.get(λ._2).get(λ._1))))
      HttpClient.setRegistersRequest(writes)
    }
  }


  /**
    Sets a bitfield with given value
    */
  private def setBitField(b: BitField, reg: Int, bits: Int): Int = {

    val legalValue = b.registerMap.map(_.contains(bits)).getOrElse(true)
    val legalLength = (1 << b.nBits) > bits

    if(legalLength){
      if(!legalValue)
        println("Illegal value")

      setBitField_(reg, bits, b.start - (b.nBits - 1), b.start)
    }
    else{
      println("Illegal length")
      0
    }
  }


  /**
    modifies the bits of a single register
    */
  private def setBitField_(reg: Int, bits: Int, firstBit: Int, lastBit: Int): Int = {


    val bitsToSet = (lastBit - firstBit)
    val mask = (1 << bitsToSet) - 1
    val shiftedMask = mask << firstBit
    val shiftedBits = bits << firstBit
    val cleared = ~(~reg | shiftedMask)
    val set = cleared | shiftedBits

    // {
    //   import utilz.asNdigitBinary
    //   val offset = (-1 + 32 - lastBit)
    //   val offsets = ("" /: (0 until offset))((a,b) => a ++ " ")
    //   val points = ("" /: (0 until (lastBit - firstBit)))((a,b) => a ++ "^")
    //   println(s"setting bits ${asNdigitBinary(bits, (1 + lastBit - firstBit))} " ++
    //             s"from positions $lastBit to $firstBit\n")

    //   println(s"init:        ${asNdigitBinary(reg, 32)}")
    //   println(s"set:         ${offsets}${asNdigitBinary(bits, (1 + lastBit - firstBit))}")
    //   println(s"to:          ${asNdigitBinary(set, 32)}")
    //   println(s"             ${offsets}${points}")
    //   println()
    //   println(s"result in decimal: ${set}")
    // }

    set
  }


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
}
