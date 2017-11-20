package cyborg


import cats.effect.IO
import cats.effect._


import HttpClient._
import DspRegisters._
import scala.util.Random
import twiddle._

object DspComms {

  import twiddle._

  // implicit class regSetInfix(val reg: RegisterAddress){ def :=(that: DSPWordValue): (RegisterAddress,DSPWordValue) = (this.reg, that) }

  // import DspRegisters._


  import fs2._
  def stimuliRequestSink(throttle: Int = 1000): Sink[IO,List[Double]] = {

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
    _.through(utilz.mapN(throttle, _.toArray.head)).evalMap(stimuliRequest)
  }


  // /**
  //   Coalesces a bunch of bit field reads, avoiding superflous reads.
  //   */
  // def readBitFields(bs: Set[BitField]): IO[RegisterReadResponse] = {
  //   val addressList = bs.map(_.address).toSet.toList
  //   HttpClient.readRegistersRequest(RegisterReadList(addressList))
  // }


  // /**
  //   Coalesces a bunch of bit field set operations, avoiding both
  //   superflous reads and writes.
  //   */
  // def writeBitFields(bs: List[(BitField, DSPFieldValue)]): IO[String] = {
  //   val reads: IO[RegisterReadResponse] = readBitFields(bs.map(_._1).toSet)
  //   val partitionedByAddress = bs.groupBy(_._1.address)
  //   val collapsed = partitionedByAddress.map{ a =>
  //     val address = a._1
  //     val writeVals: List[(BitField, DSPFieldValue)] = a._2
  //     (address, (µ: DSPWordValue) => writeVals.foldLeft(µ)( (reg, λ) => setBitField(λ._1, reg, λ._2)))
  //   }

  //   reads flatMap{ response =>
  //     val registers = (response.values zip response.addresses)
  //     val writes = RegisterSetList(registers.map(λ => (λ._2, collapsed.get(λ._2).get(λ._1))))
  //     HttpClient.setRegistersRequest(writes)
  //   }
  // }


  // /**
  //   Sets a bitfield with given value
  //   */
  // private def setBitField(b: BitField, reg: Int, bits: Int): Int = {

  //   val legalLength = (1 << b.size) > bits

  //   if(legalLength){

  //     setBitField_(reg, bits, b.startBit - (b.size - 1), b.startBit)
  //   }
  //   else{
  //     println("Illegal length")
  //     0
  //   }
  // }


  // /**
  //   modifies the bits of a single register
  //   */
  // private def setBitField_(reg: Int, bits: Int, firstBit: Int, lastBit: Int): Int = {


  //   val bitsToSet = (lastBit - firstBit)
  //   val mask = (1 << bitsToSet) - 1
  //   val shiftedMask = mask << firstBit
  //   val shiftedBits = bits << firstBit
  //   val cleared = ~(~reg | shiftedMask)
  //   val set = cleared | shiftedBits

  //   set
  // }


  // def pingTest(): IO[Unit] = {
  //   val rnd = new Random()
  //   val addressList = List(DEBUG1,DEBUG2,DEBUG3,DEBUG4)
  //   val testVals = List.fill(4)(0x1000 + rnd.nextInt(100))
  //   val regSet = RegisterSetList(addressList, testVals)
  //   val regRead = RegisterReadList(addressList)

  //   for {
  //     write <- setRegistersRequest(regSet)
  //     read  <- readRegistersRequest(regRead)
  //   } yield {
  //     if(read.values.zip(testVals).map(λ => (λ._1 == λ._2)).foldLeft(true)(_&&_)){
  //       println(Console.GREEN + "Ping test OK" + Console.RESET)
  //     }
  //     else{
  //       println(Console.GREEN + "Ping test failed!!" + Console.RESET)
  //     }
  //   }
  // }
}
