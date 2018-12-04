package cyborg.dsp.stimulus
import cyborg._

import scala.concurrent.duration._
import utilz._
import cats.effect.IO

import DspRegisters._
import HttpClient.DSP._
import bonus._


object WaveformGenerator {


  val dspTimeStep: FiniteDuration = 20.micro
  val dspVoltageOffset = 0x8000
  val mVperUnit = 0.571

  def mvToDSPVoltage(mv: mV): Int = (mv*mVperUnit).toInt + dspVoltageOffset
  def DSPvoltageToMv(word: Int): mV = (word - dspVoltageOffset)*(1.0/mVperUnit)

  // Simply generates and adds datapoints from a function for some range of time
  def uploadWave(
    duration: FiniteDuration,
    channel: Int,
    generator: FiniteDuration => mV): IO[Unit] =
  {

    val mcsChannel = channel*2
    val channelAddress = (mcsChannel * 4) + 0x9f20
    say(s"Uploading wave to channel $channel. address: 0x${channelAddress.toHexString}, SBS address: 0x${(channelAddress + 4).toHexString}")

    // for instance 2 seconds, 20 µs per point means we need 2s/20µs = 100 000 points
    val totalpoints = (duration/dspTimeStep).toInt

    case class StimPoint(ticks: Int, voltage: Int)
    val maxVoltage = 1000
    val minVoltage = -maxVoltage

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


    val stimResetAddress = 0x920c + (mcsChannel*0x20)
    val SBSResetAddress  = 0x920c + ((mcsChannel+1)*0x20)

    val stimReset   = RegisterSetList(List(0x0 -> stimResetAddress))
    val SBSReset    = RegisterSetList(List(0x0 -> SBSResetAddress))
    val stimUploads = RegisterSetList(stimWords.map(λ => (λ.invoke, channelAddress)))
    val SBSUploads  = RegisterSetList(SBSWords.map(λ  => (λ.invoke, channelAddress + 4)))

    import cats.syntax._
    import cats.implicits._

    val isValid = points.map(p => if ((DSPvoltageToMv(p.voltage) > maxVoltage) || (DSPvoltageToMv(p.voltage) < minVoltage)){
                               say(s"mike pence point detected: $p")
                               None
                             }
                             else {
                               Some(p)
                             }
    ).sequence.isDefined

    import HttpClient._

    if(isValid)
      for {
        _ <- setRegistersRequest(stimReset)
        _ <- setRegistersRequest(stimUploads)
        _ <- setRegistersRequest(SBSReset)
        _ <- setRegistersRequest(SBSUploads)
      } yield ()
    else
      for {
        _ <- Fsay[IO]("!!!! STIMULUS IS OUT OF RANGE")
        _ <- Fsay[IO]("Here's a mike pence error asshole")
        _ <- IO.raiseError(new Exception("Mike Pence error"))
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

  /**
    Balanced signifies that the voltage integral over t is close to 0 which is better for electrode health.
    Thus no offset or low/high duration
    */
  def balancedSquareWave(
    channel: Int,
    period: FiniteDuration,
    amplitude: mV): IO[Unit] = {

    val lowDuration: FiniteDuration = period/2
    def generator(t: FiniteDuration) = if (t > lowDuration) amplitude/2 else -(amplitude/2)

    uploadWave(period, channel, generator)
  }
}
