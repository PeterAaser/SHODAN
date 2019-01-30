package cyborg.dsp.stimulus
import cats.MonadError
import cats.effect.{ ContextShift, Sync }
import cyborg._

import scala.concurrent.duration._
import utilz._

import DspRegisters._
import bonus._


class WaveformGenerator[F[_]: Sync](client: MEAMEHttpClient[F]) {

  /**
    A stimpoint represents a given voltage which should be driven for n ticks.
    These are squished into stimWords before being written to DSP
    */
  case class StimPoint(ticks: Int, voltage: Int){
    override def toString: String = s"The voltage ${voltage.toHexString} for $ticks ticks"
  }
  object StimPoint {
    def toStimWords(points: List[StimPoint]): List[StimWord] = {
      val stimWords = points.flatMap{ stimPoint =>
        val longWord = if((stimPoint.ticks / 1000) > 0)
                         Some(StimWord(1000, (stimPoint.ticks / 1000), stimPoint.voltage))
                       else
                         None

        val shortWord = if(((stimPoint.ticks + 1) % 1000) > 0)
                          Some(StimWord(1, stimPoint.ticks % 1000, stimPoint.voltage))
                        else
                          None

        List(longWord, shortWord).flatten
      }
      stimWords
    }
  }


  /**
    A stimword represents a 32 bit word that will drive its stimValue for
    repeats intervals of 20µs (or 2000µs if timeBase != 1)
    */
  case class StimWord(timeBase: Int, repeats: Int, stimValue: Int){
    def invoke: Int = {
      val timeWord = if(timeBase == 1) 0 else (1 << 25)
      val repeatWord = repeats << 16
      timeWord | repeatWord | stimValue
    }

    def asVoltage: Double = (stimValue - dspVoltageOffset)*mVperUnit

    override def toString: String = {
      val timeString = if(timeBase == 1) "of 20µs" else "of 2000µs"
      val voltString = "%d".format((stimValue*mVperUnit).toInt)
      s"stimValue: 0x${stimValue.toHexString}. invoked to 0x${invoke.toHexString}\n" +
      s"A command to set the voltage to $voltString for $repeats repeats $timeString"
    }
  }


  /**
    A sideband word describes what the electrodes should do as data is streamed
    */
  case class SBSWord(timeBase: Int, repeats: Int){

    import spire.syntax.literals.radix._
    val amplifierProtection  = x2"00000001"
    val stimSelect           = x2"00010000"
    val stimSwitch           = x2"00001000"

    val timeWord = if(timeBase == 1) 0 else (1 << 26)
    val repeatWord = repeats << 16

    def invoke: Int = {
      amplifierProtection | stimSelect | stimSwitch | timeWord | repeatWord
    }
  }
  object SBSWord {
    def createSBSWords(ticks: Int): List[SBSWord] = {
      val longPoints  = ticks / 1000
      val shortPoints = ticks % 1000


      val longWord = if(longPoints > 0)
                       Some(SBSWord(1000, longPoints - 1))
                     else
                       None

      val shortWord = if(shortPoints > 0)
                       Some(SBSWord(1, shortPoints - 1))
                     else
                       None

      val endWord = Some(SBSWord(1, 10))

      List(longWord, shortWord, endWord).flatten
    }

    def decipher(sbsWord: Int): Unit = {
      val configId = sbsWord.getField(15, 8)
      val stimSelect = sbsWord.getBit(4)
      val stimSwitch = sbsWord.getBit(3)
      val ampProtect = sbsWord.getBit(0)

      val timeBase = sbsWord.getBit(26)
      val repeats = sbsWord.getField(25, 10)

      val vecType = sbsWord.getField(30, 3) match {
        case 0 => "DAC/SBS data vector"
        case 1 => "Loop pointer vector"
        case 2 => "Long loop pointer vector"
        case 3 => "Loop pointer counter vector"
        case 7 => "END command"
      }

      say("SBS word decode:")
      say(s"type is $vecType")
      val tbString = if(timeBase) "20000 µs" else "20µs"

      say(s"time base is $tbString")
      say(s"repeats: $repeats")
      say(s"params are: stimSelect: $stimSelect, stimSwitch: $stimSwitch, ampProtect: $ampProtect")
      say(s"config ID for list mode: 0x${configId.toHexString}")

    }
  }


  val dspTimeStep: FiniteDuration = 20.micro
  val dspVoltageOffset = 0x8000
  val mVperUnit = 0.571

  def mvToDSPVoltage(mv: mV): Int = (mv*mVperUnit).toInt + dspVoltageOffset
  def DSPvoltageToMv(word: Int): mV = (word - dspVoltageOffset)*(1.0/mVperUnit)

  // Simply generates and adds datapoints from a function for some range of time
  def uploadWave(
    duration: FiniteDuration,
    channel: Int,
    generator: FiniteDuration => mV,
    repeats: Int = 1): F[Unit] =
  {

    val mcsChannel = channel*2
    val channelAddress = (mcsChannel * 4) + 0x9f20
    say(s"Uploading wave to channel $channel. address: 0x${channelAddress.toHexString}, SBS address: 0x${(channelAddress + 4).toHexString}")

    // for instance 2 seconds, 20 µs per point means we need 2s/20µs = 100 000 points
    val totalpoints = (duration/dspTimeStep).toInt
    say(s"total amount of points needed: $totalpoints")

    val maxVoltage = 1000
    val minVoltage = -maxVoltage

    // Generates a list of steps and duration in multiple of 20µs the step should be held
    val points: List[StimPoint] = (0 until totalpoints)
      .map(_*20.micro)
      .map(generator)
      .map(_/mVperUnit).map(_.toInt)
      .map(_ + dspVoltageOffset)
      .foldLeft((List[StimPoint](), 0, 0)){
        (x, voltage) => {
          val (stimPoints, previousVoltage, ticks) = x
          val shouldUpdate = voltage != previousVoltage
          if(shouldUpdate) {
            val sp = StimPoint(ticks, voltage)
            (sp :: stimPoints, voltage, 0)
          }
          else {
            (stimPoints, previousVoltage, ticks + 1)
          }
        }
      }._1.reverse



    val stimWords = StimPoint.toStimWords(points)

    // TODO wrong
    val SBSWords = SBSWord.createSBSWords(totalpoints)

    val stimResetAddress = 0x920c + (mcsChannel*0x20)
    val SBSResetAddress  = 0x920c + ((mcsChannel+1)*0x20)

    val stimReset   = RegisterSetList(List(0x0 -> stimResetAddress))
    val SBSReset    = RegisterSetList(List(0x0 -> SBSResetAddress))
    val stimUploads = RegisterSetList(stimWords.map(x => (x.invoke, channelAddress)))
    val SBSUploads  = RegisterSetList(SBSWords.map(x  => (x.invoke, channelAddress + 4)))

    import cats.syntax._
    import cats.implicits._

    val isValid = stimWords.map(p => if ((p.asVoltage > maxVoltage) || (p.asVoltage < minVoltage)){
                               say(s"mike pence point detected: $p")
                               None
                             }
                             else {
                               Some(p)
                             }
    ).sequence.isDefined

    import client.DSP._

    if(isValid)
      for {
        _ <- setRegistersRequest(stimReset)
        _ <- setRegistersRequest(stimUploads)
        _ <- setRegistersRequest(SBSReset)
        _ <- setRegistersRequest(SBSUploads)
      } yield ()
    else
      for {
        _         <- Fsay[F]("!!!! STIMULUS IS OUT OF RANGE")
        _         <- Fsay[F]("Here's a mike pence error asshole")
        _:F[Unit] <- Sync[F].raiseError(new Exception("Mike Pence error"))
      } yield ()
  }


  def sineWave(channel: Int, period: FiniteDuration, amplitude: mV): F[Unit] = {

    val w = 2.0*math.Pi/(period/1.second)
    val a = amplitude/mVperUnit
    def generator(t: FiniteDuration) = math.sin(w*(t/1.seconds))*a

    uploadWave(period, channel, generator)

  }

  def squareWave(
    channel: Int,
    lowDuration: FiniteDuration,
    period: FiniteDuration,
    offset: mV,
    amplitude: mV): F[Unit] = {

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
    amplitude: mV): F[Unit] = {

    val lowDuration: FiniteDuration = period/2
    def generator(t: FiniteDuration) = if (t > lowDuration) amplitude/2 else -(amplitude/2)

    uploadWave(period, channel, generator)
  }
}
