package cyborg

import org.http4s.circe._

import _root_.io.circe.literal._
import _root_.io.circe.generic.auto._
import _root_.io.circe.syntax._
import _root_.io.circe.{ Encoder, Decoder, Json }

import cyborg.DspRegisters._
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

object MEAMEmessages {

  case class DAQparams(samplerate: Int, segmentLength: Int)
  case class DspFuncCall(func: Int, args: List[(Int, Int)]){
    import cyborg.dsp.calls.DspCalls._
    def decodeSetPeriod: Option[(Int, FiniteDuration)] =
      if (func == SET_ELECTRODE_GROUP_PERIOD)
        Some((args(0)._1, args(1)._1.fromDSPticks))
      else
        None

    def decodeToggleGroup: Option[(Int, Boolean)] =
      if (func == ENABLE_STIM_GROUP)
        Some((args(0)._1, true))
      else if (func == DISABLE_STIM_GROUP)
        Some((args(0)._1, false))
      else
        None
  }
  object DspFuncCall {
    def apply(func: Int, args: (Int,Int)*): DspFuncCall = {
      DspFuncCall(func, args.toList)
    }
  }

  case class MEAMEhealth(isAlive: Boolean, dspAlive: Boolean)
  case class MEAMEstatus(isAlive: Boolean, dspAlive: Boolean, dspBroken: Boolean)

  case class DspFCS(func: Int, argAddrs: List[Int], argVals: List[Int]){
    def toFC: DspFuncCall = DspFuncCall(func, argAddrs zip argVals)
  }
  object DspFCS {
    def fromFC(fc: DspFuncCall): DspFCS = {
      val (words, addrs) = fc.args.unzip
      DspFCS( fc.func, addrs, words )
    }
  }

  implicit val DspFCSCodec = jsonOf[IO, DspFCS]
  implicit val DspFuncCallCodec = DspFCSCodec.map(_.toFC)
  implicit val DspFuncCallEncoder: Encoder[DspFuncCall] = x => DspFCS.fromFC(x).asJson

  implicit val regSetCodec      = jsonOf[IO, RegisterSetList]
  implicit val DAQdecoder       = jsonOf[IO, DAQparams]
  implicit val regReadCodec     = jsonOf[IO, RegisterReadList]
  implicit val regReadRespCodec = jsonOf[IO, RegisterReadResponse]
  implicit val MEAMEhealthCodec = jsonOf[IO, MEAMEhealth]
  implicit val MEAMEstatusCodec = jsonOf[IO, MEAMEstatus]
}
