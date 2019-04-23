package cyborg

import cyborg._

import monocle.Lens
import monocle.macros.GenLens

import com.avsystem.commons.serialization.HasGenCodec
import com.avsystem.commons.serialization.GenCodec
import io.udash.properties.HasModelPropertyCreator
import io.udash.rpc.HasGenCodecAndModelPropertyCreator
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import cats.kernel.Eq
object State {

  import Settings._

  case class ProgramState(
    dataSource        : Option[DataSource],
    currentExperiment : Option[CurrentExperiment],
    dsp               : DSPstate,
    meame             : MEAMEstate,
    isRecording       : Boolean,
    isRunning         : Boolean){

    val canStartPlayback = !isRunning
    val canStartLive = meame.alive && !isRunning

    val canStop          = isRunning

    val canStopRecording = isRecording
    val canFlash         = !dsp.isFlashed

    val canRecord = dataSource.map{
      case Live => isRunning
      case _    => false
    }.getOrElse(false) && !isRecording
  }

  case class DSPstate(
    isFlashed   : Boolean = false,
    dspResponds : Boolean = false)

  case class MEAMEstate(
    alive : Boolean = false)

  val meameL : Lens[ProgramState, MEAMEstate] = GenLens[ProgramState](_.meame)
  val dspL   : Lens[ProgramState, DSPstate]   = GenLens[ProgramState](_.dsp)

  object DSPstate extends HasGenCodec[DSPstate] {
    def default: DSPstate = DSPstate(false, false)
  }

  object MEAMEstate extends HasGenCodec[MEAMEstate] {
    def default: MEAMEstate = MEAMEstate(false)
  }

  object DataSource extends HasGenCodec[DataSource]
  object CurrentExperiment extends HasGenCodec[CurrentExperiment]
  object ProgramState extends HasGenCodecAndModelPropertyCreator[ProgramState] {
    def apply(): ProgramState = ProgramState(
      None,
      None,
      DSPstate.default,
      MEAMEstate.default,
      false,
      false
    )
    def init = apply
  }

  sealed trait DataSource
  case object Live extends DataSource
  case class Playback(id: Int) extends DataSource

  sealed trait CurrentExperiment
  case object Maze extends CurrentExperiment

  implicit val eqPS: Eq[ProgramState] = Eq.fromUniversalEquals
}

object Settings {
  import mcsChannelMap._

  case class DAQSettings(
    samplerate         : Int,
    segmentLength      : Int)

  case class FilterSettings(
    spikeCooldown   : Int,
    maxSpikesPerSec : Int,
    threshold       : Int)

  case class OptimizerSettings(
    decimationFactor : Int,
    ticksPerEval     : Int,
    dataSetWindow    : Int){
    val dataSetSize  : Int = ticksPerEval/decimationFactor
  }

  case class GAsettings(

    generationSize  : Int,
    childrenPerGen  : Int,
    mutantsPerGen   : Int){
    val dropPerGen  : Int = childrenPerGen + mutantsPerGen}

  case class ReadoutSettings(
    inputChannels     : List[Int],
    weightMin         : Double,
    weightMax         : Double,
    MAGIC_THRESHOLD   : Int,
    ANNinternalLayout : List[Int],
    bucketSizeMillis  : Int,
    buckets           : Int,
    outputs           : Int){
    val getLayout         : List[Int] = 10*buckets :: ANNinternalLayout ::: List(outputs)}
    // val getLayout         : List[Int] = 9 :: ANNinternalLayout ::: List(outputs)}

  case class DSPsettings(
    blanking           : Boolean,
    blankingProtection : Boolean,
    allowed            : List[Int],
    stimulusElectrodes : List[List[Int]])

  case class SourceSettings(
    meameIP     : String,

    apiPort     : Int,
    dataPort    : Int,
    sendBufSize : Int,
    recvBufSize : Int,
    format      : String,
    readBufSize : Int)

  case class FullSettings(
    daq       : DAQSettings,
    readout   : ReadoutSettings,
    ga        : GAsettings,
    dsp       : DSPsettings,
    filter    : FilterSettings,
    optimizer : OptimizerSettings,
    source    : SourceSettings)


  ////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////
  ////  DEFAULTS
  object DAQSettings extends HasGenCodec[DAQSettings] {
    val default = DAQSettings(
      samplerate         = 10000,
      segmentLength      = 1000
    )}

  object GAsettings extends HasGenCodec[GAsettings] {
    val default = GAsettings(

      generationSize = 2000,
      childrenPerGen = 1000,
      mutantsPerGen  = 500

      // generationSize = 1000,
      // childrenPerGen = 500,
      // mutantsPerGen  = 200

      // generationSize = 500,
      // childrenPerGen = 200,
      // mutantsPerGen  = 100

      // generationSize = 200,
      // childrenPerGen = 100,
      // mutantsPerGen  = 50
    )}

  object ReadoutSettings extends HasGenCodec[ReadoutSettings] {

    val default = ReadoutSettings(
      weightMin         = -2.0,
      weightMax         = 2.0,
      MAGIC_THRESHOLD   = 1000,
      ANNinternalLayout = List(), // empty list encodes a network with no hidden outputs
      outputs           = 2,
      bucketSizeMillis  = 20,
      buckets           = 50,
      inputChannels     = List(16, 17, 18, 19, 20, 21,
        24, 25, 26, 27, 28, 29,
        32, 33, 34, 35, 36, 37,
        40, 41, 42, 43, 44, 45).map(getMCSdataChannel),
    )}

  object DSPsettings extends HasGenCodec[DSPsettings] {
    val default = DSPsettings(
      blanking           = true,
      blankingProtection = true,
      allowed            = List(0,1,2),
      stimulusElectrodes = List(
        List(54, 55, 56, 57, 58, 59),
        List(0,   1,  2,  3,  4,  5),
        List(6,  14, 22,     38, 46),
      ).map(_.map(getMCSstimChannel)),
    )
  }

  object FilterSettings extends HasGenCodec[FilterSettings] {
    val default = FilterSettings(
      spikeCooldown   = 10,
      maxSpikesPerSec = 10,
      threshold       = 1
    )
  }

  object SourceSettings extends HasGenCodec[SourceSettings] {
    val mock = SourceSettings(
      meameIP     = "0.0.0.0",
      apiPort     = 8888,
      dataPort    = params.Network.tcpPort,
      sendBufSize = 4096,
      recvBufSize = 262144,
      format      = "JVM",
      readBufSize = 1024*1024
    )

    val live = SourceSettings(
      meameIP     = "10.20.92.130",
      apiPort     = 8888,
      dataPort    = params.Network.tcpPort,
      sendBufSize = 4096,
      recvBufSize = 262144,
      format      = "Csharp",
      readBufSize = 1024*1024
    )
  }

  object OptimizerSettings extends HasGenCodec[OptimizerSettings] {
    val default = OptimizerSettings(
      decimationFactor = 200,
      ticksPerEval     = 6000,
      dataSetWindow    = 3
    )
  }


  object FullSettings extends HasGenCodecAndModelPropertyCreator[FullSettings] {
    val default = FullSettings(
      DAQSettings.default,
      ReadoutSettings.default,
      GAsettings.default,
      DSPsettings.default,
      FilterSettings.default,
      OptimizerSettings.default,
      if(params.Network.mock) SourceSettings.mock else SourceSettings.live
    )
  }

  implicit val eqFS: Eq[FullSettings] = Eq.fromUniversalEquals
}
