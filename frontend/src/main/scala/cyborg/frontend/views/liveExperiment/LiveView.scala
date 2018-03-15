package cyborg.frontend.views

import cyborg.frontend.routing._
import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.utils.Icons
import scala.util.Success

import cyborg._
import utilz._

import io.udash.css._

import org.scalajs.dom
import scalatags.JsDom.all._
import io.udash.bootstrap.button._
import org.scalajs.dom
import scalatags.JsDom
import JsDom.all._

import scala.concurrent.ExecutionContext.Implicits.global


class LiveView(model: ModelProperty[LiveModel], presenter: LivePresenter) extends FinalView with CssView {

  val playButton = UdashButton()(Icons.FontAwesome.play)
  val pauseButton = UdashButton()(Icons.FontAwesome.pause)
  val recordButton = UdashButton()(Icons.FontAwesome.circle)
  val stopButton = UdashButton()(Icons.FontAwesome.square)

  val waweformCantvas =

  override def getTemplate: Modifier = {
    div(
      UdashBootstrap.loadFontAwesome(),
      playButton.render,
      pauseButton.render,
      recordButton.render,
      stopButton.render,
    ),
  }

}

class LivePresenter(model: ModelProperty[LiveModel]) extends Presenter[LiveState.type] {

  override def handleState(state: LiveState.type): Unit = {}


}

case class LiveModel(isRunning: Boolean, isRecording: Boolean)
object LiveModel extends HasModelPropertyCreator[LiveModel]

case object LiveViewFactory extends ViewFactory[LiveState.type] {
  override def create(): (View, Presenter[LiveState.type]) = {
    val model = ModelProperty( LiveModel(false, false) )
    val presenter = new LivePresenter(model)
    val view = new LiveView(model, presenter)
    (view, presenter)
  }
}
