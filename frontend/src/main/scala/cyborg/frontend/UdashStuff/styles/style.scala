package cyborg

import io.udash.css.CssBase
import io.udash.css._
import scalacss.internal.{ AV, Length }

object StyleConstants extends CssBase {
  import dsl._
  import scala.language.postfixOps
  import scalacss.internal.Macros.Color

  /**
    * COLORS
    */
  object Colors {
    val Red = c"#e30613"
    val RedLight = c"#ff2727"
    val RedDark = c"#a6031b"
    val Grey = c"#898989"
    val GreyExtra = c"#ebebeb"
    val GreySemi = c"#cfcfd6"
    val GreySuperDark = c"#1c1c1e"
    val Yellow = c"#ffd600"
    val Cyan = c"#eef4f7"
  }

  def border(bColor: Color = StyleConstants.Colors.GreyExtra, bWidth: Length[Double] = 1.0 px, bStyle: AV = borderStyle.solid): CssStyle = style(
    borderWidth(bWidth),
    bStyle,
    borderColor(bColor)
  )

  val frame = style(
    border(),
    display.block,
    padding(1.5 rem),
    margin(2 rem, `0`)
  )
}

object ForceBootstrap {
  import scalatags.JsDom.all._

  def apply(modifiers: Modifier*): Modifier =
    div(cls := "bootstrap")( //force Bootstrap styles
      modifiers:_*
    )
}
