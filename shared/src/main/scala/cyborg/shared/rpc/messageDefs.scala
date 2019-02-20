package cyborg

import cyborg._

import com.avsystem.commons.serialization.HasGenCodec
import com.avsystem.commons.serialization.GenCodec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import io.udash.rpc.HasGenCodecAndModelPropertyCreator


/**
  This thing might not need to exist
  */
object RPCmessages {

  import Settings._

  /**
    This is a kind of annoying stopgap between the DB ExperimentInfo case class and
    the ad hoc stuff I needed then and there
    TODO: Make a doobie query for this stuff
    */
  case class RecordingInfo(
    daqSettings        : DAQSettings,
    id                 : Int,
    date               : String,
    duration           : Option[String],
    MEA                : Option[Int],
    comment            : String,
    )
  object RecordingInfo extends HasGenCodecAndModelPropertyCreator[RecordingInfo]


  case class DrawCommand(
    yMin: Int,
    yMax: Int,
    color: Int)
  object DrawCommand extends HasGenCodecAndModelPropertyCreator[DrawCommand]
}
