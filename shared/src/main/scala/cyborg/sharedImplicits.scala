package cyborg

import scodec._
import scodec.codecs._

import wallAvoid._

object sharedImplicits {

  implicit val IntVectorCodec = scodec.codecs.vectorOfN(scodec.codecs.uint16, scodec.codecs.int32)

  // I'm very sorry.
  // Just look away
  implicit val doubleTupleCodec: Codec[Double ~ Double] = double ~ double
  implicit val coordCodec: Codec[Coord] =
    doubleTupleCodec.widenOpt(Coord.apply, Coord.unapply)

  implicit val agentContentsTupleCodec: Codec[Coord ~ Double ~ Int] = coordCodec ~ double ~ int32
  implicit val agentCodec: Codec[Agent] =
    agentContentsTupleCodec.widenOpt(
      Agent.apply,
      Agent.unapply(_).map(λ => ((λ._1,λ._2),λ._3))
    )
  // Okay you can look again now


}
