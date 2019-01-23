package cyborg.frontend.views

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

class LiveView(model: ModelProperty[LiveModel], presenter: LivePresenter, wfCanvas: html.Canvas, agentCanvas: html.Canvas) extends ContainerView with CssView {

  val rangeUp = UdashButton()(Icons.FontAwesome.plus)
  val rangeDown = UdashButton()(Icons.FontAwesome.minus)

  val playButton = UdashButton()(Icons.FontAwesome.play)
  val recordButton = UdashButton()(Icons.FontAwesome.circle)
  val stopRecordButton = UdashButton()(Icons.FontAwesome.timesCircle)
  val stopButton = UdashButton()(Icons.FontAwesome.square)

  val hurr = ButtonStyle

  playButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlayClicked(btn) }
  recordButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onRecordClicked(btn) }
  stopButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onStopClicked(btn) }
  stopRecordButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onStopRecordingClicked(btn) }

  rangeUp.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onRangeUpClicked(btn) }
  rangeDown.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onRangeDownClicked(btn) }

  model.streamTo( playButton.disabled, initUpdate = true)(m => !m.canPlay)
  model.streamTo( recordButton.disabled, initUpdate = true)(m => !m.canRecord)
  model.streamTo( stopRecordButton.disabled, initUpdate = true)(m => m.canRecord)
  model.streamTo( stopButton.disabled, initUpdate = true)(m => !m.canStop)

  val uploadButton = UdashButton()("flash DSP")
  val stopTestButton = UdashButton()("stop test")


  override def getTemplate: Modifier = {
    div(
      UdashBootstrap.loadFontAwesome(),
      rangeUp.render,
      rangeDown.render,
      wfCanvas.render,
      agentCanvas.render,
      playButton.render,
      showIf(model.subProp(_.isRecording).transform(!_))(recordButton.render),
      showIf(model.subProp(_.isRecording))(stopRecordButton.render),
      stopButton.render,
      ul(
        li(p("is running"), bind(model.subProp(_.isRunning))).render,
        li(p("is recording"), bind(model.subProp(_.isRecording))).render,
        li(p("can play"), bind(model.transform(_.canPlay))).render,
        li(p("can stop"), bind(model.transform(_.canStop))).render,
        li(p("can record"), bind(model.transform(_.canRecord))).render,
        ),
      childViewContainer
    ),
  }
}


class LivePresenter(model: ModelProperty[LiveModel], wfCanvas: html.Canvas, agentCanvas: html.Canvas) extends Presenter[LiveState.type] {

  import cyborg.frontend.services.rpc._
  import cyborg.frontend.Context

  val wfQueue = new scala.collection.mutable.Queue[Array[Int]]()
  val agentQueue = new scala.collection.mutable.Queue[Agent]()
  agentQueue.enqueue(Agent.init)

  def onPlayClicked(btn: UdashButton) = {
    say("play canvas clicked")
    say("registering waveform stuff")
    WfClient.register(wfQueue)
    model.subProp(_.isRunning).set(true)

    say("registering agent stuff")
    AgentClient.register(agentQueue)

    say("play shows all green")
    Context.serverRpc.startLive
    Context.serverRpc.startAgent
  }

  def onRangeUpClicked(btn: UdashButton) = {
    val current = wf.getMaxVal
    if(current > 128)
      wf.setMaxVal(current/2)
  }

  def onRangeDownClicked(btn: UdashButton) = {
    val current = wf.getMaxVal
    if(current <= 64000)
      wf.setMaxVal(current*2)
  }

  def onRecordClicked(btn: UdashButton) = {
    model.subProp(_.isRecording).set(true)
    Context.serverRpc.startRecording
  }


  // Should stop visualizing as well, but it don't
  def onStopClicked(btn: UdashButton) = {
    say("stop canvas clicked")
    WfClient.unregister()
    model.subProp(_.isRunning).set(false)
    Context.serverRpc.stopRecording
    wfQueue.clear()
  }

  def onStopRecordingClicked(btn: UdashButton) = {
    say("stop canvas clicked")
    WfClient.unregister()
    model.subProp(_.isRecording).set(false)
    Context.serverRpc.stopRecording
    wfQueue.clear()
  }

  var wf: cyborg.waveformVisualizer.WFVisualizerControl = null
  var ag: cyborg.Visualizer.VisualizerControl = null

  override def handleState(state: LiveState.type): Unit = {
    wf = new cyborg.waveformVisualizer.WFVisualizerControl(wfCanvas, wfQueue)
    ag = new cyborg.Visualizer.VisualizerControl(agentCanvas, agentQueue)
  }
}


case object LiveViewFactory extends ViewFactory[LiveState.type] {

  override def create(): (View, Presenter[LiveState.type]) = {

    val wfCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
    val agentCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

    val model = ModelProperty( LiveModel(false, false, RecordingForm()) )
    val presenter = new LivePresenter(model, wfCanvas, agentCanvas)
    val view = new LiveView(model, presenter, wfCanvas, agentCanvas)
    (view, presenter)
  }
}


case class LiveModel(isRunning: Boolean, isRecording: Boolean, recFormInfo: RecordingForm){
  def canPlay = !isRunning
  def canRecord = isRunning && !isRecording
  def canStop = isRunning
}
object LiveModel extends HasModelPropertyCreator[LiveModel]

case class RecordingForm(comment: String = "No description provided", cultureId: Option[Int] = None)
object RecordingForm extends HasModelPropertyCreator[RecordingForm]
