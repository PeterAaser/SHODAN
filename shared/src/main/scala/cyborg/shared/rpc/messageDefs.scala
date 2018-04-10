package cyborg

import cyborg._

import com.avsystem.commons.serialization.HasGenCodec
import com.avsystem.commons.serialization.GenCodec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext

object RPCmessages {

  import Setting._

  // TODO find a less asinine way to work with time
  // The problem is pretty much that there is no easy way to get the same jodatime backend and frontend
  case class RecordingInfo(
    experimentSettings : ExperimentSettings,
    id                 : Int,
    date               : String,
    duration           : Option[String],
    MEA                : Option[Int],
    comment            : String,
    )
  object RecordingInfo extends HasGenCodec[RecordingInfo]


  sealed trait EquipmentFailure
  object EquipmentFailure {
    implicit val codec: GenCodec[EquipmentFailure] =
      GenCodec.materialize
  }
  case object DspDisconnected    extends EquipmentFailure
  case object DspBroken          extends EquipmentFailure
  case object MEAMEoffline       extends EquipmentFailure
  case object NoIdeaCheckConsole extends EquipmentFailure


  type EquipmentState = Either[List[EquipmentFailure], Unit]

}
