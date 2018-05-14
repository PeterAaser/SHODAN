package cyborg.frontend.views

import cyborg.frontend.routing._
import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.utils.Icons
import scala.util.{ Failure, Success }

import cyborg._
import frontilz._

import io.udash.css._

import org.scalajs.dom.document
import scalatags.JsDom.all._
import org.scalajs.dom.html
import io.udash.bootstrap.button._

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalajs.dom.document
import org.scalajs.dom.html

class MEApresenter(model: ModelProperty[MEAmodel]) extends Presenter[MEAstate] {
  override def handleState(state: MEAstate): Unit = {
    state match {
      case MEAstate(Some(id)) =>
        // Look up stuff
    }
  }
}

case class MEAviewFactory(id: Option[Int]) extends ViewFactory[MEAstate] {
  override def create(): (View, Presenter[MEAstate]) = ???
}

class MEAview(model: ModelProperty[MEAmodel], presenter: MEApresenter) extends FinalView with CssView {
  override def getTemplate: Modifier = ???
}

case class MEAmodel(id: Option[Int], comment: Option[String])
case object MEAmodel extends HasModelPropertyCreator[MEAmodel]
