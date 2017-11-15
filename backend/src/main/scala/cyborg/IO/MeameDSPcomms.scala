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

   // val desc = vision.map(toStimFrequency( logScaleBuilder(scala.math.E), _)).mkString("\n[","]\n[","]")
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


  // TODO Add bounds check et al
  def setBitField(reg: Int, bits: Int, firstBit: Int, lastBit: Int, printMe: String = "no"): Int = {

    def asNdigitBinary (source: Int, digits: Int): String = {
      val l: java.lang.Long = source.toBinaryString.toLong
      String.format ("%0" + digits + "d", l) }

    val bitsToSet = (lastBit - firstBit + 1)
    val mask = (math.pow(2, bitsToSet).toInt) - 1
    val shiftedMask = mask << firstBit
    val shiftedBits = bits << firstBit
    val cleared = ~(~reg | shiftedMask)
    val set = cleared | shiftedBits

    if(printMe == "print me"){
      println(s"for reg with value: ${asNdigitBinary(reg, 8)} " ++
                s"setting bits ${asNdigitBinary(bits, (lastBit - firstBit + 1))} " ++
                s"from $lastBit to $firstBit")

      println(s"bits to set: ${asNdigitBinary(bitsToSet, 8)}")
      println(s"mask: ${asNdigitBinary(mask ,8)}")
      println(s"shiftedMask: ${asNdigitBinary(shiftedMask ,8)}")
      println(s"shiftedBits: ${asNdigitBinary(shiftedBits ,8)}")
      println(s"cleared: ${asNdigitBinary(cleared ,8)}")
      println(s"set: ${asNdigitBinary(set, 8)}")
    }

    set
  }

}
