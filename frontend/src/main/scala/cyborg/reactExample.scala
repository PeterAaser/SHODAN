package cyborg

import org.scalajs.dom.ext.KeyCode
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import org.scalajs.dom.html
import scalatags.JsDom.all._
import org.scalajs.dom.raw.MouseEvent

object reactExample {
  val OuterX    = 600
  val OuterY    = 240
  val InnerSize =  24
  val MoveDist  =  24

  case class State(x: Int, y: Int)

  def initState = State((OuterX - InnerSize) / 2, (OuterY - InnerSize) / 2)

  val OuterDiv =
    <.div(
      ^.tabIndex   := 0,
      ^.width      := OuterX.px,
      ^.height     := OuterY.px,
      ^.border     := "solid 1px #333",
      ^.background := "#ddd")

  val InnerDiv =
    <.div(
      ^.position.relative,
      ^.width      := InnerSize.px,
      ^.height     := InnerSize.px,
      ^.background := "#800")

  def moveOneAxis(pos: Int, steps: Int, max: Int): Int =
    (pos + steps * MoveDist) min (max - InnerSize) max 0

  class Backend($: BackendScope[Unit, State]) {
    private var outerRef: html.Element = _

    def init: Callback =
      Callback(outerRef.focus())

    def move(dx: Int, dy: Int): Callback =
      $.modState(s => s.copy(
                   x = moveOneAxis(s.x, dx, OuterX),
                   y = moveOneAxis(s.y, dy, OuterY)))

    def handleKey(e: ReactKeyboardEvent): Callback = {

      def plainKey: CallbackOption[Unit] =             // CallbackOption will stop if a key isn't matched
        CallbackOption.keyCodeSwitch(e) {
          case KeyCode.Up    => move(0, -1)
          case KeyCode.Down  => move(0,  1)
          case KeyCode.Left  => move(-1, 0)
          case KeyCode.Right => move( 1, 0)
        }

      def ctrlKey: CallbackOption[Unit] =              // Like above but if ctrlKey is pressed
        CallbackOption.keyCodeSwitch(e, ctrlKey = true) {
          case KeyCode.Up    => move(0, -OuterY)
          case KeyCode.Down  => move(0,  OuterY)
          case KeyCode.Left  => move(-OuterX, 0)
          case KeyCode.Right => move( OuterX, 0)
        }

      (plainKey orElse ctrlKey) >> e.preventDefaultCB  // This is the interesting part.
                                                       //
                                                       // orElse joins CallbackOptions so if one fails, it tries the other.
                                                       //
                                                       // The >> means "and then run" but only if the left side passes.
                                                       // This means preventDefault only runs if a valid key is pressed.
    }

    def render(s: State) =
      OuterDiv.ref(outerRef = _)(
        ^.onKeyDown ==> handleKey,
        InnerDiv(
          ^.left := s.x.px,
          ^.top  := s.y.px))
  }

  val Main = ScalaComponent.builder[Unit]("CallbackOption example")
    .initialState(initState)
    .renderBackend[Backend]
    .componentDidMount(_.backend.init)
    .build
}
