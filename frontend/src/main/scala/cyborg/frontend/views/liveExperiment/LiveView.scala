package cyborg.frontend.views

import cyborg.frontend.routing._
import io.udash._
import io.udash.bootstrap.UdashBootstrap
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


class LiveView(model: ModelProperty[LiveModel], presenter: LivePresenter) extends FinalView {

  override def getTemplate: Modifier = {
    div(
      p("yo")
    ),
  }

}

class LivePresenter(model: ModelProperty[LiveModel]) extends Presenter[LiveState.type] {
  override def handleState(state: LiveState.type): Unit = {}
}

case class LiveModel(hurr: Boolean)
object LiveModel extends HasModelPropertyCreator[LiveModel]

case object LiveViewFactory extends ViewFactory[LiveState.type] {
  override def create(): (View, Presenter[LiveState.type]) = {
    val model = ModelProperty( LiveModel(false) )
    val presenter = new LivePresenter(model)
    val view = new LiveView(model, presenter)
    (view, presenter)
  }
}
