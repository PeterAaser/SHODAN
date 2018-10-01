package cyborg

import scala.concurrent.duration._
import utilz._
import cats.effect.IO

import DspRegisters._
import bonus._


object WaveformGenerator {


  val dspTimeStep: FiniteDuration = 20.micro
  val dspVoltageOffset = 0x8000
  val mVperUnit = 0.571

  // Simply generates and adds datapoints from a function for some range of time
  def uploadWave(
    duration: FiniteDuration,
    channel: Int,
    generator: FiniteDuration => mV): IO[Unit] =
  {

    val channelAddress = (channel * 4) + 0x9f20

    // for instance 2 seconds, 20 µs per point means we need 2s/20µs = 100 000 points
    val totalpoints = (duration/dspTimeStep).toInt

    case class StimPoint(ticks: Int, voltage: Int)

    // Generates a list of steps and duration in multiple of 20µs the step should be held
    val points = (0 until totalpoints)
      .map(_*20.micro)
      .map(generator)
      .map(_/mVperUnit).map(_.toInt)
      .map(_ + dspVoltageOffset)
      .foldLeft((List[StimPoint](), 0, 0)){
        (λ, voltage) => {
          val (stimPoints, previousVoltage, ticks) = λ
          val shouldUpdate = voltage != previousVoltage
          if(shouldUpdate)
            (StimPoint(ticks, voltage) :: stimPoints, voltage, 0)
          else
            (stimPoints, previousVoltage, ticks + 1)
        }
      }._1

    say(points)

    case class StimWord(timeBase: Int, repeats: Int, stimValue: Int){
      def invoke: Int = {
        val timeWord = if(timeBase == 1) 0 else (1 << 25)
        val repeatWord = repeats << 16
        timeWord | repeatWord | stimValue
      }
      override def toString: String = {
        val timeString = if(timeBase == 1) "of 20µs" else "of 2000µs"
        val voltString = "%2f".format(stimValue*mVperUnit)
        s"A command to set the voltage to $voltString for $repeats repeats $timeString"
      }
    }
    case class SBSWord(timeBase: Int, repeats: Int){

      import spire.syntax.literals.radix._
      val amplifierProtection  = x2"00000001"
      val stimSelect           = x2"00010000"
      val stimSwitch           = x2"00001000"

      val timeWord = if(timeBase == 1) 0 else (1 << 25)
      val repeatWord = repeats << 16

      def invoke: Int = {
        amplifierProtection | stimSelect | stimSwitch | timeWord | repeatWord
      }
    }


    val stimWords = points.flatMap{ λ =>
      val longWord = if((λ.ticks / 1000) > 0)
                       List(StimWord(1000, (λ.ticks / 1000), λ.voltage))
                     else
                       Nil

      val shortWord = if((λ.ticks % 1000) > 0)
                        List(StimWord(1, λ.ticks % 1000, λ.voltage))
                      else
                        Nil

      longWord ::: shortWord
    }

    val SBSWords = List(
      SBSWord(1000, totalpoints/1000),
      SBSWord(1, (totalpoints % 1000) + 1)
    )


    say(stimWords)
    say(SBSWords)

    val stimResetAddres = 0x920c + (channel*0x20)
    val SBSResetAddres = 0x920c + ((channel+1)*0x20)

    val stimReset = RegisterSetList(List(0x0 -> stimResetAddres))
    val SBSReset = RegisterSetList(List(0x0 -> SBSResetAddres))
    val stimUploads = RegisterSetList(stimWords.map(λ => (λ.invoke, channelAddress)))
    val SBSUploads = RegisterSetList(SBSWords.map(λ => (λ.invoke, channelAddress + 4)))

    import HttpClient._

    for {
      _ <- setRegistersRequest(stimReset)
      _ <- setRegistersRequest(stimUploads)
      _ <- setRegistersRequest(SBSReset)
      _ <- setRegistersRequest(SBSUploads)
    } yield ()
  }


  def sineWave(channel: Int, period: FiniteDuration, amplitude: mV): IO[Unit] = {

    val w = (period/1.second)*2.0*math.Pi
    val a = amplitude/mVperUnit
    def generator(t: FiniteDuration) = math.sin(w*t/1.seconds)*a

    uploadWave(period, channel, generator)

  }

  def squareWave(
    channel: Int,
    lowDuration: FiniteDuration,
    period: FiniteDuration,
    offset: mV,
    amplitude: mV): IO[Unit] = {

    val z: mV = 0

    def generator(t: FiniteDuration) = offset + (if (t > lowDuration) amplitude else (z))

    uploadWave(period, channel, generator)
  }
}
