package cyborg.frontend.views

import cyborg.frontend._
import cyborg.frontend.Context
import cyborg.frontend.routing._
import cyborg.RPCmessages._
import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.utils.{ Icons, UdashListGroup }
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

class RecordingView(model: ModelProperty[RecordingModel],
                    presenter: RecordingPresenter,
                    wfCanvas: html.Canvas,
                    recordings: SeqProperty[RecordingInfo],
                    selectedRecord: Property[RecordingInfo],
                    conf: Property[Settings.FullSettings]) extends FinalView with CssView {

  val playButton = UdashButton()(Icons.FontAwesome.play)
  val pauseButton = UdashButton()(Icons.FontAwesome.pause)
  val stopButton = UdashButton()(Icons.FontAwesome.square)

  val listThingy = UdashListGroup(recordings){ rec =>
    val btn = UdashButton()(compz.renderDBrecordSmall(rec))
    btn.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.recordingClicked(rec.get) }
    btn.render
  }

  playButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlayClicked(btn) }
  pauseButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPauseClicked(btn) }
  stopButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onStopClicked(btn) }

  override def getTemplate: Modifier = {
    div(
      UdashBootstrap.loadFontAwesome(),
      listThingy.render,
      compz.renderDBrecord(selectedRecord),
      showIf(model.subProp(_.recordingSelected))(
        div(
          playButton.render,
          pauseButton.render,
          stopButton.render,
        ).render
      ),
      showIf(model.subProp(_.recordingSelected))(wfCanvas.render)
    )
  }
}


class RecordingPresenter(model: ModelProperty[RecordingModel],
                         wfCanvas: html.Canvas,
                         recordings: SeqProperty[RecordingInfo],
                         selectedRecord: Property[RecordingInfo],
                         conf: Property[Settings.FullSettings]) extends Presenter[RecordingState.type] {


  import cyborg.frontend.services.rpc._
  import cyborg.frontend.Context

  val wfQueue = new scala.collection.mutable.Queue[Array[Int]]()

  def onPlayClicked(btn: UdashButton) = {
    say("play record clicked")
    WfClient.register(wfQueue)
    Context.serverRpc.startPlayback(selectedRecord.get)
  }


  // TODO: Kinda doesnt work
  def onPauseClicked(btn: UdashButton) = {
    say("pause record clicked")
    WfClient.unregister()
    wfQueue.clear()
  }


  def onStopClicked(btn: UdashButton) = {
    WfClient.unregister()
    wfQueue.clear()
  }


  def recordingClicked(rec: RecordingInfo) = {
    selectedRecord.set(rec)
    model.set(model.get.copy(recordingSelected = true))
    conf.set( conf.get.copy( daq = conf.get.daq.copy( samplerate = rec.daqSettings.samplerate, segmentLength = rec.daqSettings.segmentLength )))
    new cyborg.waveformVisualizer.WFVisualizerControl(wfCanvas, wfQueue)
  }


  override def handleState(state: RecordingState.type): Unit = {
    Context.serverRpc.getRecordings.onComplete {
      case Success(recs) => recordings.set(recs)
      case Failure(ex) => say(s"get rec state failed with $ex")
    }
  }
}

case class RecordingModel(isRunning: Boolean, isRecording: Boolean, recordingSelected: Boolean)
object RecordingModel extends HasModelPropertyCreator[RecordingModel]

case object RecordingViewFactory extends ViewFactory[RecordingState.type] {

  override def create(): (View, Presenter[RecordingState.type]) = {

    val wfCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

    val recordings = SeqProperty[RecordingInfo]
    val selectedRecord = Property.empty[RecordingInfo]
    val model = ModelProperty( RecordingModel(false, false, false) )
    val conf = Property[Settings.FullSettings](Settings.FullSettings.default)

    val presenter = new RecordingPresenter(model, wfCanvas, recordings, selectedRecord, conf)
    val view = new RecordingView(model, presenter, wfCanvas, recordings, selectedRecord, conf)
    (view, presenter)
  }
}
