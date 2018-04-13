package cyborg.frontend.views

import cyborg.frontend.routing._

import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.navs.UdashNavbar
import io.udash.bootstrap.utils.Icons
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

import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._

import io.udash.css._

import org.scalajs.dom.{window, File}
import rx._

import scala.concurrent.ExecutionContext.Implicits.global


class DspTestView(model: ModelProperty[DspTestModel], presenter: DspTestViewPresenter) extends ContainerView with CssView {

  val stopButton = UdashButton()(Icons.FontAwesome.square)
  val uploadButton = UdashButton()("flash DSP")

  val test1 = UdashButton()("Stimulus upload baseline")
  val test2 = UdashButton()("Stimulus upload replicate")
  val test3 = UdashButton()("Stimulus upload square wave")
  val test4 = UdashButton()("Stim queue test 1")
  val test5 = UdashButton()("Stim queue test 2")
  val test6 = UdashButton()("Stim queue test 3")
  val test7 = UdashButton()("All electrodes squarewave test")

  stopButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.stopTestClicked(btn) }
  uploadButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.flashClicked(btn) }
  test1.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.testClicked(btn, 0) }
  test2.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.testClicked(btn, 1) }
  test3.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.testClicked(btn, 2) }
  test4.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.testClicked(btn, 3) }
  test5.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.testClicked(btn, 4) }
  test6.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.testClicked(btn, 5) }
  test7.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.testClicked(btn, 6) }

  override def getTemplate: Modifier = {
    div(
      UdashBootstrap.loadFontAwesome(),
      stopButton.render,
      uploadButton.render,
      test1.render,
      test2.render,
      test3.render,
      test4.render,
      test5.render,
      test6.render,
    ),
  }
}


class DspTestViewPresenter(model: ModelProperty[DspTestModel]) extends Presenter[DspTestState.type] {

  import cyborg.frontend.Context

  def testClicked(btn: UdashButton, idx: Int): Unit = {
    say(s"launching test $idx")
    Context.serverRpc.runDspTest(idx)
  }

  def stopTestClicked(btn: UdashButton): Unit = {
    say(s"stopping test")
    Context.serverRpc.stopDspTest
  }

  def flashClicked(btn: UdashButton): Unit = {
    say(s"flashing dsp")
    Context.serverRpc.flashDsp
  }

  override def handleState(state: DspTestState.type): Unit = {}
}


case object DspTestViewFactory extends ViewFactory[DspTestState.type] {

  import scala.concurrent.duration._
  import cyborg.frontend.Context
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[DspTestState.type]) = {
    val model = ModelProperty( DspTestModel( Setting.FullSettings.default, false, false) )
    val presenter = new DspTestViewPresenter(model)
    val view = new DspTestView(model, presenter)
    (view, presenter)
  }
}

case class DspTestModel(conf: Setting.FullSettings, confReady: Boolean, stateReady: Boolean)
object DspTestModel extends HasModelPropertyCreator[DspTestModel]
