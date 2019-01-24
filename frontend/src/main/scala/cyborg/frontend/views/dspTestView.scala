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


class DspTestView(model: ModelProperty[DspTestModel], presenter: DspTestViewPresenter) extends ContainerView with CssView {

  val checkboxProps = SeqProperty[Int]((0 to 10))
  val checkboxStrings = checkboxProps.transform(
    (i: Int) => i.toString(),
    (s: String) => s.toInt
  )

  val stopButton = UdashButton()(Icons.FontAwesome.square)
  val flashButton = UdashButton()("flash DSP")
  val startStimQueue = UdashButton()("Start stim queue")

  val uploadSineButton = UdashButton()("upload square wave")
  val uploadSquareButton = UdashButton()("upload sine wave")

  val amplitude = UdashForm.numberInput()("Amplitude in mV")(model.subProp(_.amplitude).transform(_.toString, _.toInt))
  val period = UdashForm.numberInput()("period in ms")(model.subProp(_.period).transform(_.toString, _.toInt))


  val labels = (0 to 59).toList.map(_.toString())

  def shouldBreak(idx: Int): Boolean = {
    if((idx > 6) && (idx < 53))
      (idx % 8) - 6 == 0
    else
      idx == 6 || idx == 54
  }

  def electrodeSelector = form(
    UdashInputGroup()(
      UdashInputGroup.addon("EleCTrOdES:DDDD"),
      CheckButtons(
        property = checkboxStrings,
        options = labels,

        decorator = (els: Seq[(Input, String)]) => {
          span(
            els.foldLeft(List[scalatags.JsDom.Modifier]()){case (xs, z) =>
              val(i, l: String) = z
              if(shouldBreak(l.toInt)){
                label(i, l) :: br :: xs
              }
              else
                label(i, l) :: xs
            }.reverse
          )
        }
      )
    ).render
  )

  stopButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.stopTestClicked(btn) }
  flashButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.flashClicked(btn) }
  startStimQueue.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.startStimQueue(btn, checkboxProps.get) }
  uploadSineButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.sineUploadClicked(btn) }
  uploadSquareButton.listen { case UdashButton.ButtonClickEvent(btn, _)=> presenter.squareUploadClicked(btn) }

  override def getTemplate: Modifier = {
    div(
      UdashBootstrap.loadFontAwesome(),
      stopButton.render,
      flashButton.render,
      electrodeSelector.render,
      startStimQueue.render,
      br,
      p("Current waveform:"), p(bind(model.subProp(_.currentWaveform))),
      UdashForm(
        amplitude,
        period
      ).render,
      uploadSineButton.render,
      uploadSquareButton.render,
      childViewContainer
    )
  }
}


class DspTestViewPresenter(model: ModelProperty[DspTestModel]) extends Presenter[DspTestState.type] {

  import cyborg.frontend.Context

  def stopTestClicked(btn: UdashButton): Unit = {
    say(s"stopping test")
    Context.serverRpc.stopDspTest
  }

  def flashClicked(btn: UdashButton): Unit = {
    say(s"flashing dsp")
    Context.serverRpc.flashDsp
  }

  def squareUploadClicked(btn: UdashButton): Unit = {
    say("square upload clicked")
    model.subProp(_.currentWaveform).set("uploading...")
    val amp = model.subProp(_.period).get
    val ms = model.subProp(_.amplitude).get
    Context.serverRpc.uploadSquare(ms, amp)
      .onComplete {
        case Success(_) =>
          model.subProp(_.currentWaveform).set(s"Square wave, amplitude ${amp}mV, period: ${ms}ms")

        case Failure(ex) => ()
          model.subProp(_.currentWaveform).set(s"It's fucking broken...")
      }
  }

  def sineUploadClicked(btn: UdashButton): Unit = {
    say("square upload clicked")
    model.subProp(_.currentWaveform).set("uploading...")
    val amp = model.subProp(_.period).get
    val ms = model.subProp(_.amplitude).get
    Context.serverRpc.uploadSine(ms, amp)
      .onComplete {
        case Success(_) =>
          model.subProp(_.currentWaveform).set(s"Sine wave, amplitude ${amp}mV, period: ${ms}ms")

        case Failure(ex) => ()
          model.subProp(_.currentWaveform).set(s"It's fucking broken...")
      }
  }

  def startStimQueue(btn: UdashButton, checkboxes: Seq[Int]): Unit = {
    say("for fan nu kor vi grabbar!")
    Context.serverRpc.runDspTestWithElectrodes(checkboxes.toList)
  }

  override def handleState(state: DspTestState.type): Unit = {}
}


case object DspTestViewFactory extends ViewFactory[DspTestState.type] {

  import scala.concurrent.duration._
  import cyborg.frontend.Context
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[DspTestState.type]) = {
    val model = ModelProperty( DspTestModel( Settings.FullSettings.default, false, false, 100, 1000) )
    val presenter = new DspTestViewPresenter(model)
    val view = new DspTestView(model, presenter)
    (view, presenter)
  }
}

case class DspTestModel(conf: Settings.FullSettings,
                        confReady: Boolean,
                        stateReady: Boolean,
                        amplitude: Int,
                        period: Int,
                        currentWaveform: String = "Default")
object DspTestModel extends HasModelPropertyCreator[DspTestModel]
