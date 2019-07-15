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

import RPCmessages._

sealed trait StimReq {
  def group: Int
}
case class DisableStim(group: Int) extends StimReq
case class SetPeriod(group: Int, period: FiniteDuration) extends StimReq

// object StimReq extends GenCodec[StimReq] {
object StimReq {
  import com.avsystem.commons.serialization.{Input, Output}
  implicit val codec = new GenCodec[StimReq] {
    override def write(output: Output, value: StimReq): Unit = {
      val values = output.writeList()
      value match {
        case DisableStim(idx) => {
          values.writeElement().writeInt(0)
          values.writeElement().writeInt(idx)
        }
        case SetPeriod(idx, period) => {
          values.writeElement().writeInt(1)
          values.writeElement().writeInt(idx)
          values.writeElement().writeLong(period.toMillis)
        }
      }
      values.finish()
    }
    override def read(input: Input): StimReq = {
      val list = input.readList()
      val i = list.nextElement().readInt()
      if(i == 0){
        val group = list.nextElement().readInt()
        DisableStim(group)
      }
      else{
        val group = list.nextElement().readInt()
        val p = list.nextElement().readLong()
        SetPeriod(group, FiniteDuration(p, java.util.concurrent.TimeUnit.MILLISECONDS))
      }
    }
  }
}
