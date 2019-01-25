package cyborg

import com.avsystem.commons.serialization.{ GenCodec, HasGenCodec }
import pprint._

object Settings {
  import mcsChannelMap._

  case class DAQSettings(
    samplerate         : Int,
    segmentLength      : Int)

  case class FilterSettings(
    spikeCooldown   : Int,
    maxSpikesPerSec : Int,
    threshold      : Int
  )

  case class GAsettings(
    pipesPerGeneration      : Int,
    newPipesPerGeneration   : Int,
    newMutantsPerGeneration : Int,
    ticksPerEval            : Int
  ){
    val pipesKeptPerGeneration =
      pipesPerGeneration - (newPipesPerGeneration + newMutantsPerGeneration)
  }

  case class ReadoutSettings(
    inputChannels     : List[Int],
    weightMin         : Double,
    weightMax         : Double,
    MAGIC_THRESHOLD   : Int,
    ANNinternalLayout : List[Int],
    outputs           : Int
  ){
    val layout = inputChannels.size :: ANNinternalLayout ::: List(outputs)
  }

  case class DSPsettings(
    blanking           : Boolean,
    blankingProtection : Boolean,
    allowed            : List[Int],
    stimulusElectrodes : List[List[Int]]
  )

  case class FullSettings(
    daq     : DAQSettings,
    readout : ReadoutSettings,
    ga      : GAsettings,
    dsp     : DSPsettings,
    filter  : FilterSettings)

  ////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////
  ////  DEFAULTS
  object DAQSettings extends HasGenCodec[DAQSettings] {
    val default = DAQSettings(
      samplerate         = 20000,
      segmentLength      = 2000
    )}

  object GAsettings extends HasGenCodec[GAsettings] {
    val default = GAsettings(
      pipesPerGeneration      = 6,
      newPipesPerGeneration   = 2,
      newMutantsPerGeneration = 1,
      ticksPerEval            = 1000,
      )}

  object ReadoutSettings extends HasGenCodec[ReadoutSettings] {

    val default = ReadoutSettings(
      weightMin         = -2.0,
      weightMax         = 2.0,
      MAGIC_THRESHOLD   = 1000,
      ANNinternalLayout = List(), // empty list encodes a network with no hidden outputs
      outputs           = 2,
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


  object FullSettings extends HasGenCodec[FullSettings] {
    val default = FullSettings(
      DAQSettings.default,
      ReadoutSettings.default,
      GAsettings.default,
      DSPsettings.default,
      FilterSettings.default
    )
  }
}
