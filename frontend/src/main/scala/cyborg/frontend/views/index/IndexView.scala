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

class IndexView(model: ModelProperty[IndexModel], presenter: IndexViewPresenter) extends FinalView with CssView {

  say("hi")

  def menuButton(title: String) = UdashButton(buttonStyle = ButtonStyle.Primary)(title)

  val DAQbutton = menuButton("Live experiment")
  val DBplaybackButton = menuButton("Recorded experiment")
  val UploadButton = menuButton("Upload recording")

  DAQbutton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onDAQclick(btn) }
  DBplaybackButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlaybackClick(btn) }
  UploadButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onUploadClick(btn) }

  // model.subProp(_.canDAQstream).streamTo( DAQbutton.disabled, initUpdate = true)(!_)
  // model.subProp(_.canDBstream).streamTo( DBplaybackButton.disabled, initUpdate = true)(!_)
  // model.subProp(_.canUpload).streamTo( UploadButton.disabled, initUpdate = true)(!_)

  def mainButtons(): dom.Element = div(StyleConstants.frame)(
    UdashBootstrap.loadBootstrapStyles(),
    UdashButtonGroup(vertical=true)(
      DAQbutton.render,
      DBplaybackButton.render,
      UploadButton.render,
      ).render
  ).render

  override def getTemplate: Modifier = {
    div(
      ForceBootstrap(
        mainButtons()
      )
    ),
  }
}




class IndexViewPresenter(model: ModelProperty[IndexModel]) extends Presenter[IndexState.type] {

  import cyborg.frontend.Context

  def onDAQclick(btn: UdashButton) = {
    say("going to demo view")
    Context.serverRpc.ping(123).onComplete {
      case Success(i) => say(s"future ping was $i")
      case _ => say(s"failure")
    }

    Context.serverRpc.pingPush(123)
  }

  def onPlaybackClick(btn: UdashButton) =
    say("going to demo view")

  def onUploadClick(btn: UdashButton) =
    say("aa")

  override def handleState(state: IndexState.type): Unit = {}
}



case object IndexViewFactory extends ViewFactory[IndexState.type] {
  override def create(): (View, Presenter[IndexState.type]) = {
    val model = ModelProperty( IndexModel(false,false,false) )
    val presenter = new IndexViewPresenter(model)
    val view = new IndexView(model, presenter)
    (view, presenter)
  }
}


case class IndexModel(canDAQstream: Boolean, canDBstream: Boolean, canUpload: Boolean)
object IndexModel extends HasModelPropertyCreator[IndexModel]
