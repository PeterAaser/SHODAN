package cyborg.frontend.views

import cyborg.frontend.routing._

import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.form.{ UdashForm, UdashInputGroup }
import io.udash.bootstrap.navs.UdashNavbar
import io.udash.bootstrap.utils.Icons
import org.scalajs.dom.html
import scala.concurrent.Await
import scala.util.{ Failure, Success }

import cyborg._
import frontilz._

import io.udash.css._

import org.scalajs.dom
import org.scalajs.dom.html.Input
import scalatags.JsDom.all._
import io.udash.bootstrap.button._
import org.scalajs.dom
import scalatags.JsDom
import JsDom.all._

import scala.language.postfixOps
import io.udash.css._

import org.scalajs.dom.{window, File}
import rx._

import scala.concurrent.ExecutionContext.Implicits.global


class DspMemoryView(model: ModelProperty[DspMemoryModel], presenter: DspMemoryViewPresenter) extends ContainerView with CssView {


  override def getTemplate: Modifier = {
    div(
      h1("This is jimmy")
    )
  }

}


class DspMemoryViewPresenter(model: ModelProperty[DspMemoryModel]) extends Presenter[DspMemoryState.type] {
  import cyborg.frontend.Context

  override def handleState(state: DspMemoryState.type): Unit = {}
}


case object DspMemoryViewFactory extends ViewFactory[DspMemoryState.type] {

  import scala.concurrent.duration._
  import cyborg.frontend.Context
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[DspMemoryState.type]) = {
    val model = ModelProperty( DspMemoryModel(0, 0) )
    val presenter = new DspMemoryViewPresenter(model)
    val view = new DspMemoryView(model, presenter)
    (view, presenter)
  }
}

case class DspMemoryModel(base: Int, address: Int)

object DspMemoryModel extends HasModelPropertyCreator[DspMemoryModel]
