package cyborg

import cyborg.wallAvoid.{ Agent, Coord }
import cyborg.RPCmessages._
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}

object sharedImplicits {
  implicit val coordCodec: GenCodec[Coord] = GenCodec.materialize
  implicit val agentCodec: GenCodec[Agent] = GenCodec.materialize

  implicit val DBrecCodec: GenCodec[RecordingInfo] = GenCodec.materialize

}
