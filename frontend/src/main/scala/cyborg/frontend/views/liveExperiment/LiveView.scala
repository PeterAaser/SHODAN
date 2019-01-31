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


/**
  The visual elements such as buttons and shit go here
  */
class LiveView(model: ModelProperty[LiveModel], presenter: LivePresenter, wfCanvas: html.Canvas, agentCanvas: html.Canvas) extends ContainerView with CssView {

  val rangeUp = UdashButton()(Icons.FontAwesome.plus)
  val rangeDown = UdashButton()(Icons.FontAwesome.minus)

  val playButton = UdashButton()(Icons.FontAwesome.play)
  val recordButton = UdashButton()(Icons.FontAwesome.circle)
  val stopRecordButton = UdashButton()(Icons.FontAwesome.timesCircle)
  val stopButton = UdashButton()(Icons.FontAwesome.square)

  playButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlayClicked(btn) }
  recordButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onRecordClicked(btn) }
  stopButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onStopClicked(btn) }
  stopRecordButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onStopRecordingClicked(btn) }

  rangeUp.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onRangeUpClicked(btn) }
  rangeDown.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onRangeDownClicked(btn) }

  model.streamTo( playButton.disabled, initUpdate = true)(m => !m.state.canStart)
  model.streamTo( recordButton.disabled, initUpdate = true)(m => !m.state.canRecord)
  model.streamTo( stopRecordButton.disabled, initUpdate = true)(m => m.state.canStopRecording)
  model.streamTo( stopButton.disabled, initUpdate = true)(m => !m.state.canStop)

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
      showIf(model.subProp(_.state.isRecording).transform(!_))(recordButton.render),
      showIf(model.subProp(_.state.isRecording))(stopRecordButton.render),
      stopButton.render,
      ul(
        li(p("is running"), bind(model.subProp(_.state.isRunning))).render,
        li(p("is recording"), bind(model.subProp(_.state.isRecording))).render,
        li(p("can play"), bind(model.transform(_.state.canStart))).render,
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
class LivePresenter(model: ModelProperty[LiveModel], wfCanvas: html.Canvas, agentCanvas: html.Canvas) extends Presenter[LiveState.type] {

  import cyborg.frontend.services.rpc._
  import cyborg.frontend.Context

  val wfQueue = new scala.collection.mutable.Queue[Array[Int]]()
  val agentQueue = new scala.collection.mutable.Queue[Agent]()
  val confQueue = new scala.collection.mutable.Queue[FullSettings]()
  val stateQueue = new scala.collection.mutable.Queue[ProgramState]()
  agentQueue.enqueue(Agent.init)


  // terribly sorry
  say("hello?")
  scalajs.js.timers.setInterval(1000){
    say("checking for updates")
    if(confQueue.size > 0){
      val newest: FullSettings = confQueue.dequeueAll(_ => true).last
      model.subProp(_.conf).set(newest)
      say(s"Updated settings to $newest yo")
    }

    if(stateQueue.size > 0){
      val newest: ProgramState = stateQueue.dequeueAll(_ => true).last
      model.subProp(_.state).set(newest)
      say(s"Updated conf to $newest yo")
    }
  }


  def onPlayClicked(btn: UdashButton) = {
    model.subProp(_.state).modify{ s => s.copy(dataSource = Some(Live))}
    model.subProp(_.state).modify{ s => s.copy(isRunning = true) }
  }

  def onRecordClicked(btn: UdashButton) = model.subProp(_.state).modify{ s =>
    s.copy(isRecording = true)
  }

  def onStopClicked(btn: UdashButton) = model.subProp(_.state).modify{ s =>
    s.copy(isRunning = false, isRecording = false)
  }

  def onStopRecordingClicked(btn: UdashButton) = model.subProp(_.state).modify{ s =>
    s.copy(isRecording = false)
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


  val stateListener = model.subProp(_.state).listen{ s =>
    Context.serverRpc.setSHODANstate(s)
  }


  /**
    grep null hello
    */
  var wf: cyborg.waveformVisualizer.WFVisualizerControl = null
  var ag: cyborg.Visualizer.VisualizerControl = null


  /**
    This handles URL state, not model state. Think
    /users/
    /users/dave
    /users/john
    */
  override def handleState(state: LiveState.type): Unit = {
    wf = new cyborg.waveformVisualizer.WFVisualizerControl(wfCanvas, wfQueue)
    ag = new cyborg.Visualizer.VisualizerControl(agentCanvas, agentQueue)
    cyborg.frontend.services.rpc.Hurr.register(
      agentQueue,
      wfQueue,
      confQueue,
      stateQueue
    )

  }
}


case object LiveViewFactory extends ViewFactory[LiveState.type] {

  override def create(): (View, Presenter[LiveState.type]) = {

    val wfCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
    val agentCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

    val model = ModelProperty( LiveModel(ProgramState(), FullSettings.default) )
    val presenter = new LivePresenter(model, wfCanvas, agentCanvas)
    val view = new LiveView(model, presenter, wfCanvas, agentCanvas)
    (view, presenter)
  }
}

case class LiveModel(state: ProgramState, conf: FullSettings)
object LiveModel extends HasModelPropertyCreator[LiveModel]
