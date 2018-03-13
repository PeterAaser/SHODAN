package cyborg

import cyborg.wallAvoid.{ Agent, Coord }
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}

object sharedImplicits {
  implicit val coordCodec: GenCodec[Coord] = GenCodec.materialize
  implicit val agentCodec: GenCodec[Agent] = GenCodec.materialize
}
