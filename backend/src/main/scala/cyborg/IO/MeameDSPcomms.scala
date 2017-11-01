package cyborg



import cats.effect.IO
import cats.effect._


import HttpClient._
import DspRegisters._
import scala.util.Random

object DspComms {

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
      // 0 -> DEBUG1,
      // 0 -> DEBUG2,
      // 0 -> DEBUG3,
      // 0 -> DEBUG4,
      // 0 -> DEBUG5,
      // 0 -> DEBUG6,
      // 0 -> DEBUG7,
      // 0 -> DEBUG8,
      // 0 -> DEBUG9,
      0 -> CLEAR)

    val regWrite = RegisterSetList(regWrite_)

    for {
      read  <- setRegistersRequest(regWrite)
    } yield {
      println("debug registers cleared")
    }
  }


  def stimuliRequest(vision: List[Double]): IO[Unit] = {

    import MEAMEutilz._
    def toStimFrequency(transform: Double => Double, distance: Double): Double = {
      setDomain(transform)(distance)
    }

    val desc = vision.map(toStimFrequency( logScaleBuilder(scala.math.E), _)).mkString("\n[","]\n[","]")

    val regWrite = RegisterSetList(List(DEBUG1),List(0x0), desc)

    for {
      write <- setRegistersRequest(regWrite)
    } yield {
      println("sent stim req")
    }
  }

  import fs2._
  def stimuliRequestSink(throttle: Int = 1000): Sink[IO,List[Double]] =
    _.through(utilz.mapN(throttle, _.toArray.head)).evalMap(stimuliRequest)

}
