package cyborg.frontend.views

import cyborg.frontend.routing._
import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.utils.Icons
import scala.util.{ Failure, Success }

import cyborg._
import frontilz._

import io.udash.css._

import com.karasiq.bootstrap.Bootstrap.default._

import org.scalajs.dom.document
import scalatags.JsDom.all._
import org.scalajs.dom.html
import io.udash.bootstrap.button._

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalajs.dom.document
import org.scalajs.dom.html

class LiveView(model: ModelProperty[LiveModel], presenter: LivePresenter, wfCanvas: html.Canvas) extends FinalView with CssView {

  val playButton = UdashButton()(Icons.FontAwesome.play)
  val pauseButton = UdashButton()(Icons.FontAwesome.pause)
  val recordButton = UdashButton()(Icons.FontAwesome.circle)
  val stopButton = UdashButton()(Icons.FontAwesome.square)

  playButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlayClicked(btn) }
  recordButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlayClicked(btn) }
  stopButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlayClicked(btn) }

  override def getTemplate: Modifier = {
    say("rendering all that crap")
    div(
      UdashBootstrap.loadFontAwesome(),
      wfCanvas.render,
      playButton.render,
      recordButton.render,
      stopButton.render,
    ),
  }
}


class LivePresenter(model: ModelProperty[LiveModel], wfCanvas: html.Canvas) extends Presenter[LiveState.type] {

  import cyborg.frontend.services.rpc._
  import cyborg.frontend.Context

  val wfQueue = new scala.collection.mutable.Queue[Array[Int]]()

  def onPlayClicked(btn: UdashButton) = {
    say("play canvas clicked")
    WfClient.register(wfQueue)
  }


  def onPauseClicked(btn: UdashButton) = {
    say("pause canvas clicked")
    WfClient.unregister()
    wfQueue.clear()
  }


  def onStopClicked(btn: UdashButton) = {
    WfClient.unregister()
    wfQueue.clear()
  }


  override def handleState(state: LiveState.type): Unit = {
    // disgusting...
    // val conf = Context.serverRpc.getSHODANconfig
    // val state = Context.serverRpc.getSHODANstate

    // conf onComplete {
    //   say(wfCanvas)
    //   say("the horrible handlestate thing is done")

    //   r => r match {
    //     case Success(conf) => new cyborg.waveformVisualizer.WFVisualizerControl(wfCanvas, wfQueue)
    //     case Failure(ex) => say("egads!")
    //   }
    // }
  }
}

case class LiveModel(isRunning: Boolean, isRecording: Boolean)
object LiveModel extends HasModelPropertyCreator[LiveModel]

case object LiveViewFactory extends ViewFactory[LiveState.type] {

  override def create(): (View, Presenter[LiveState.type]) = {

    val wfCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

    val model = ModelProperty( LiveModel(false, false) )
    val presenter = new LivePresenter(model, wfCanvas)
    val view = new LiveView(model, presenter, wfCanvas)
    (view, presenter)
  }
}
