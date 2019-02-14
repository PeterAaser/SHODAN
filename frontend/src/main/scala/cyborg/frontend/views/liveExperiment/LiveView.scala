package cyborg.frontend.views

import cyborg.State._
import cyborg.Settings._
import cyborg.wallAvoid.Agent
import io.udash.bootstrap.form.UdashInputGroup
import org.scalajs.dom.html.Input
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
import scalatags.generic.TypedTag

import cyborg.frontend.services.rpc._
import cyborg.frontend.Context
import cyborg.frontend._


/**
  The visual elements such as buttons and shit go here
  */
class LiveView(model: ModelProperty[LiveModel],
               presenter: LivePresenter,
               canvasController: WaveformComp) extends ContainerView with CssView {

  val rangeUp = UdashButton()(Icons.FontAwesome.plus)
  val rangeDown = UdashButton()(Icons.FontAwesome.minus)

  val playButton = UdashButton()(Icons.FontAwesome.play)
  val recordButton = UdashButton()(Icons.FontAwesome.circle)
  val stopRecordButton = UdashButton()(Icons.FontAwesome.timesCircle)
  val stopButton = UdashButton()(Icons.FontAwesome.square)

  playButton.listen { case UdashButton.ButtonClickEvent(btn, _)       => presenter.onPlayClicked(btn) }
  recordButton.listen { case UdashButton.ButtonClickEvent(btn, _)     => presenter.onRecordClicked(btn) }
  stopButton.listen { case UdashButton.ButtonClickEvent(btn, _)       => canvasController.onStopClicked(btn) }
  stopRecordButton.listen { case UdashButton.ButtonClickEvent(btn, _) => canvasController.onStopRecordingClicked(btn) }

  rangeUp.listen { case UdashButton.ButtonClickEvent(btn, _)   => canvasController.onRangeUpClicked(btn) }
  rangeDown.listen { case UdashButton.ButtonClickEvent(btn, _) => canvasController.onRangeDownClicked(btn) }

  model.streamTo( playButton.disabled, initUpdate       = true)(m => !m.state.canStartLive)
  model.streamTo( recordButton.disabled, initUpdate     = true)(m => !m.state.canRecord)
  model.streamTo( stopRecordButton.disabled, initUpdate = true)(m => m.state.canStopRecording)
  model.streamTo( stopButton.disabled, initUpdate       = true)(m => !m.state.canStop)

  val uploadButton = UdashButton()("flash DSP")
  val stopTestButton = UdashButton()("stop test")

  override def getTemplate: Modifier = {
    div(
      UdashBootstrap.loadFontAwesome(),
      rangeUp.render,
      rangeDown.render,
      canvasController.wfCanvas.render,
      canvasController.agentCanvas.render,
      playButton.render,
      showIf(model.subProp(_.state.isRecording).transform(!_))(recordButton.render),
      showIf(model.subProp(_.state.isRecording))(stopRecordButton.render),
      stopButton.render,
      ul(
        li(p("is running"), bind(model.subProp(_.state.isRunning))).render,
        li(p("is recording"), bind(model.subProp(_.state.isRecording))).render,
        li(p("can play"), bind(model.transform(_.state.canStartLive))).render,
        li(p("can stop"), bind(model.transform(_.state.canStop))).render,
        li(p("can record"), bind(model.transform(_.state.canRecord))).render,
      ),
      childViewContainer
    ),
  }
}


/**
  The logic for handling what happens when buttons are pressed and stuff
  A middle man between the passive view and the model.
  Also handles the canvases because of reasons
  */
class LivePresenter(model: ModelProperty[LiveModel], canvasController: WaveformComp) extends Presenter[LiveState.type] {

  def onPlayClicked(btn: UdashButton) = {
    model.subProp(_.state).modify{ s => s.copy(dataSource = Some(Live))}
    model.subProp(_.state).modify{ s => s.copy(isRunning = true) }
  }

  def onRecordClicked(btn: UdashButton) = model.subProp(_.state).modify{ s =>
    s.copy(isRecording = true)
  }


  /**
    This handles URL state, not model state. Think
    /users/
    /users/dave
    /users/john
    */
  override def handleState(state: LiveState.type): Unit = { }
}


case object LiveViewFactory extends ViewFactory[LiveState.type] {

  override def create(): (View, Presenter[LiveState.type]) = {

    val model = ModelProperty( LiveModel(ProgramState(), FullSettings.default) )
    val canvasController = new WaveformComp(model.subProp(_.state), model.subProp(_.conf))

    val presenter = new LivePresenter(model, canvasController)
    val view = new LiveView(model, presenter, canvasController)
    (view, presenter)
  }
}

case class LiveModel(state: ProgramState, conf: FullSettings)
object LiveModel extends HasModelPropertyCreator[LiveModel]
