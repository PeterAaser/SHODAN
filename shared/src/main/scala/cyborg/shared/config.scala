package cyborg

import com.avsystem.commons.serialization.{ GenCodec, HasGenCodec }
import pprint._

object Setting {
  import mcsChannelMap._

  case class ExperimentSettings(
    samplerate         : Int,
    stimulusElectrodes : List[List[Int]],
    segmentLength      : Int)

  object ExperimentSettings extends HasGenCodec[ExperimentSettings] {
    val default = ExperimentSettings(
      samplerate         = 10000,
      stimulusElectrodes = List(47, 7, 12).map(getMCSchannel).map(List(_)),
      segmentLength      = 1000
    )}


  // Currently not stored
  case class GAsettings(
    pipesPerGeneration      : Int,
    newPipesPerGeneration   : Int,
    newMutantsPerGeneration : Int,
    ticksPerEval            : Int
  ){
    val pipesKeptPerGeneration =
      pipesPerGeneration - (newPipesPerGeneration + newMutantsPerGeneration)
  }
  object GAsettings extends HasGenCodec[GAsettings] {
    val default = GAsettings(
        pipesPerGeneration      = 6,
        newPipesPerGeneration   = 2,
        newMutantsPerGeneration = 1,
        ticksPerEval            = 1000,
      )}


  // Settings for the readout layer, that is the artificial neural network
  case class ReadoutSettings(
    inputChannels   : List[Int],
    weightMin       : Double,
    weightMax       : Double,
    MAGIC_THRESHOLD : Int,
    ANNlayout       : List[Int]
  )
  object ReadoutSettings extends HasGenCodec[ReadoutSettings] {

    val default = ReadoutSettings(
        weightMin       = -2.0,
        weightMax       = 2.0,
        MAGIC_THRESHOLD = 1000,
        ANNlayout       = List(), // empty list encodes a perceptron
        inputChannels   = List(0, 1, 2, 3, 4, 5, 54, 55, 56, 57, 58, 59).map(getMCSchannel)
      )}


  case class FullSettings(experimentSettings : ExperimentSettings,
                          filterSettings     : ReadoutSettings,
                          gaSettings         : GAsettings)
  object FullSettings extends HasGenCodec[FullSettings] {
    val default = FullSettings(
      ExperimentSettings.default,
      ReadoutSettings.default,
      GAsettings.default
    )
  }
}
