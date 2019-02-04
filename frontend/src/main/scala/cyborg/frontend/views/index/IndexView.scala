package cyborg.frontend.views

import cyborg.frontend.routing._

import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.navs.UdashNavbar
import org.scalajs.dom.html
import scala.concurrent.Await
import scala.util.{ Failure, Success }

import cyborg._
import frontilz._

import io.udash.css._

import org.scalajs.dom
import scalatags.JsDom.all._
import io.udash.bootstrap.button._
import org.scalajs.dom
import scalatags.JsDom
import JsDom.all._
import RPCmessages._

import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._

import org.scalajs.dom.{window, File}
import rx._

import scala.concurrent.ExecutionContext.Implicits.global

class IndexView(model: ModelProperty[IndexModel], presenter: IndexViewPresenter) extends ContainerView with CssView {

  say("hi")

  def menuButton(title: String) = UdashButton(buttonStyle = io.udash.bootstrap.button.ButtonStyle.Primary)(title)

  val DAQbutton        = menuButton("Live experiment")
  val DBplaybackButton = menuButton("Recorded experiment")
  val UploadButton     = menuButton("Upload recording")

  DAQbutton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onDAQclick(btn) }
  DBplaybackButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlaybackClick(btn) }
  UploadButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onUploadClick(btn) }

  def mainButtons(): dom.Element = div(StyleConstants.frame)(
    UdashBootstrap.loadBootstrapStyles(),
    UdashButtonGroup(vertical=true)(
      DAQbutton.render,
      DBplaybackButton.render,
      UploadButton.render,
      ).render,
  ).render

  override def getTemplate: Modifier = {
    div(
      mainButtons(),
      ul(
        b("MEAME state:"),
      ).render,

      ul(
        b("Conf:"),
        li(produce(model)(m => b(s"samplerate is ${m.conf.daq.samplerate}").render)),
        li(produce(model)(m => b(s"segment length is ${m.conf.daq.samplerate}").render)),
      ),
      childViewContainer
    ),
  }
}


class IndexViewPresenter(model: ModelProperty[IndexModel]) extends Presenter[IndexState.type] {

  import cyborg.frontend.Context

  def onDAQclick(btn: UdashButton)      = Context.applicationInstance.goTo(LiveState)
  def onPlaybackClick(btn: UdashButton) = Context.applicationInstance.goTo(RecordingState)
  def onUploadClick(btn: UdashButton)   = Context.applicationInstance.goTo(LiveState)

  override def handleState(state: IndexState.type): Unit = {}
}


case object IndexViewFactory extends ViewFactory[IndexState.type] {

  import scala.concurrent.duration._
  import cyborg.frontend.Context
  import scala.concurrent.ExecutionContext.Implicits.global

  say("index view factory called")

  override def create(): (View, Presenter[IndexState.type]) = {
    val model = ModelProperty( IndexModel(Settings.FullSettings.default) )
    val presenter = new IndexViewPresenter(model)
    val view = new IndexView(model, presenter)
    (view, presenter)
  }
}

case class IndexModel(conf: Settings.FullSettings)
object IndexModel extends HasModelPropertyCreator[IndexModel]
