package cyborg

import cyborg._

import monocle.Lens
import monocle.macros.GenLens

import com.avsystem.commons.serialization.HasGenCodec
import com.avsystem.commons.serialization.GenCodec
import io.udash.properties.HasModelPropertyCreator
import io.udash.rpc.HasGenCodecAndModelPropertyCreator
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import cats.kernel.Eq

case class VizState(
  selectedChannel: Int,
  zoomLevel: Int,
  timeCompressionLevel: Int
)

object VizState extends HasGenCodecAndModelPropertyCreator[VizState] {
  val default = VizState(0, 5, 1)
  implicit val eqFS: Eq[VizState] = Eq.fromUniversalEquals
}
