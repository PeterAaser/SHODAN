package cyborg

import cyborg._

import com.avsystem.commons.serialization.HasGenCodec
import com.avsystem.commons.serialization.GenCodec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

object RPCmessages {

  import Settings._

  // TODO find a less asinine way to work with time
  // The problem is pretty much that there is no easy way to get the same jodatime backend and frontend
  case class RecordingInfo(
    daqSettings        : DAQSettings,
    id                 : Int,
    date               : String,
    duration           : Option[String],
    MEA                : Option[Int],
    comment            : String,
    )
  object RecordingInfo extends HasGenCodec[RecordingInfo]


  sealed trait DataSource
  case object Live extends DataSource
  case class Playback(id: Int) extends DataSource

  sealed trait CurrentExperiment
  case object Maze extends CurrentExperiment

  case class ProgramState(
    dataSource        : Option[DataSource],
    currentExperiment : Option[CurrentExperiment],
    dsp               : DSPstate,
    meame             : MEAMEstate
  )

  case class DSPstate(
    isFlashed   : Boolean = false,
    dspResponds : Boolean = false
  )

  case class MEAMEstate(
    meameResponds : Boolean = false
 )

  object DSPstate extends HasGenCodec[DSPstate] {
    def default: DSPstate = DSPstate(false, false)
  }

  object MEAMEstate extends HasGenCodec[MEAMEstate] {
    def default: MEAMEstate = MEAMEstate(false)
  }

  object DataSource extends HasGenCodec[DataSource]
  object CurrentExperiment extends HasGenCodec[CurrentExperiment]
  object ProgramState extends HasGenCodec[ProgramState] {
    def apply(): ProgramState = ProgramState(
      None,
      None,
      DSPstate.default,
      MEAMEstate.default
    )
  }
}
