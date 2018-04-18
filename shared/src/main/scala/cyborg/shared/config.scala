package cyborg

import com.avsystem.commons.serialization.{ GenCodec, HasGenCodec }
import pprint._

object Setting {
  case class ExperimentSettings(
    samplerate         : Int,
    stimulusElectrodes : List[Int],
    segmentLength      : Int)

  object ExperimentSettings extends HasGenCodec[ExperimentSettings] {
    val default = ExperimentSettings(
      samplerate         = 20000,
      stimulusElectrodes = Nil,
      segmentLength      = 2000
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


  case class FilterSettings(
    inputChannels   : List[Int],
    outputChannels  : List[List[Int]],
    weightMin       : Double,
    weightMax       : Double,
    MAGIC_THRESHOLD : Int,
    ANNlayout       : List[Int]
  )
  object FilterSettings extends HasGenCodec[FilterSettings] {
    val default = FilterSettings(
        weightMin       = -2.0,
        weightMax       = 2.0,
        MAGIC_THRESHOLD = 1000,
        ANNlayout       = List(2,2),
        inputChannels   = List(0, 1),
        outputChannels  = List(List(0,1), List(12,13), List(22), List(29))
      )}


  case class FullSettings(experimentSettings : ExperimentSettings,
                          filterSettings     : FilterSettings,
                          gaSettings         : GAsettings)
  object FullSettings extends HasGenCodec[FullSettings] {
    val default = FullSettings(
      ExperimentSettings.default,
      FilterSettings.default,
      GAsettings.default
    )
  }
}
