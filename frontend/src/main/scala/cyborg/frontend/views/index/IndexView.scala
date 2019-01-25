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

  val DAQbutton = menuButton("Live experiment")
  val DBplaybackButton = menuButton("Recorded experiment")
  val UploadButton = menuButton("Upload recording")
  val DspTestButton = menuButton("Test DSP")

  DAQbutton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onDAQclick(btn) }
  DBplaybackButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlaybackClick(btn) }
  UploadButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onUploadClick(btn) }

  def mainButtons(): dom.Element = div(StyleConstants.frame)(
    UdashBootstrap.loadBootstrapStyles(),
    UdashButtonGroup(vertical=true)(
      DAQbutton.render,
      DBplaybackButton.render,
      UploadButton.render,
      DspTestButton.render,
      ).render,
  ).render

  def renderEquipmentFailure(m: Property[EquipmentState]) = {
    produce(m){ m =>
      m match {
        case Right(_) => p("All systems green").render
        case Left(errors) => {
          val online = if(errors.contains(MEAMEoffline)) p("MEAME is offline") else p("MEAME online")
          val dspAlive = if(errors.contains(DspDisconnected)) p("DSP is offline") else p("DSP online")
          val dspBreak = if(errors.contains(DspBroken)) p("DsP iS broKeN") else p("DSP working")
          ul(
            li(online),
            li(dspAlive),
            li(dspBreak)
          ).render
        }
      }
    }
  }

  override def getTemplate: Modifier = {
    div(
      mainButtons(),
      ul(
        b("MEAME state:"),
        li(
          renderEquipmentFailure(model.subProp(_.meameState))
        ),
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

  Context.serverRpc.getSHODANstate.onComplete{
    case Success(nextState) => model.subProp(_.meameState).set(nextState)
    case Failure(ex) => say(s"failed with $ex")
  }


  def onDAQclick(btn: UdashButton) = say("Does nothing")
  def onPlaybackClick(btn: UdashButton) = say("going to demo view")
  def onUploadClick(btn: UdashButton) = say("aa")

  override def handleState(state: IndexState.type): Unit = {}

}


case object IndexViewFactory extends ViewFactory[IndexState.type] {

  import scala.concurrent.duration._
  import cyborg.frontend.Context
  import scala.concurrent.ExecutionContext.Implicits.global

  say("index view factory called")

  override def create(): (View, Presenter[IndexState.type]) = {
    val model = ModelProperty( IndexModel( Settings.FullSettings.default, Right(())) )
    val presenter = new IndexViewPresenter(model)
    val view = new IndexView(model, presenter)
    (view, presenter)
  }
}

case class IndexModel(conf: Settings.FullSettings, meameState: EquipmentState)
object IndexModel extends HasModelPropertyCreator[IndexModel]
