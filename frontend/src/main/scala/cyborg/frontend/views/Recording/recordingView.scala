package cyborg.frontend.views

import cyborg.State._
import cyborg.Settings._
import cyborg.wallAvoid.Agent
import cyborg.frontend._
import cyborg.frontend.Context
import cyborg.frontend.routing._
import cyborg.RPCmessages._
import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.utils._
import scala.util.{ Failure, Success }


import cyborg._
import frontilz._

import io.udash.css._

import cyborg.frontend.services.rpc._
import cyborg.frontend.Context

import org.scalajs.dom.document
import scalatags.JsDom.all._
import org.scalajs.dom.html
import io.udash.bootstrap.button._

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalajs.dom.document
import org.scalajs.dom.html

class RecordingView(model: ModelProperty[RecordingModel],
                    presenter: RecordingPresenter,
                    recordings: SeqProperty[RecordingInfo],
                    canvasController: WaveformComp) extends FinalView with CssView {

  val rangeUp = UdashButton()(UdashIcons.FontAwesome.plus)
  val rangeDown = UdashButton()(UdashIcons.FontAwesome.minus)

  rangeUp.listen { case UdashButton.ButtonClickEvent(btn, _) => canvasController.onRangeUpClicked(btn) }
  rangeDown.listen { case UdashButton.ButtonClickEvent(btn, _) => canvasController.onRangeDownClicked(btn) }

  val playButton = UdashButton()(UdashIcons.FontAwesome.play)
  val pauseButton = UdashButton()(UdashIcons.FontAwesome.pause)
  val stopButton = UdashButton()(UdashIcons.FontAwesome.square)

  val listThingy = UdashListGroup(recordings){ rec =>
    val btn = UdashButton()(compz.renderDBrecordSmall(rec))
    btn.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.recordingClicked(rec.get) }
    btn.render
  }

  playButton.listen { case UdashButton.ButtonClickEvent(btn, _) => presenter.onPlayClicked(btn) }
  stopButton.listen { case UdashButton.ButtonClickEvent(btn, _) => canvasController.onStopClicked(btn) }

  model.streamTo( playButton.disabled, initUpdate = true)(m => !m.state.canStartPlayback)
  model.streamTo( stopButton.disabled, initUpdate = true)(m => !m.state.canStop)

  val huh: org.scalajs.dom.Element = listThingy.render

  override def getTemplate: Modifier = {
    div(
      UdashBootstrap.loadFontAwesome(),
      listThingy.render,
      showIf(model.subProp(_.recordingSelected).transform(_.isDefined)){
        div(
          compz.renderDBrecord(model.subProp(_.recordingSelected))
        ).render
      },
      showIf(model.subProp(_.recordingSelected).transform(_.isDefined)){
        val huhh = div(
          playButton.render,
          pauseButton.render,
          stopButton.render,
        )
        huhh.render
      },
      rangeUp.render,
      rangeDown.render,
      showIf(model.subProp(_.recordingSelected).transform(_.isDefined))(canvasController.wfCanvas.render),
      showIf(model.subProp(_.recordingSelected).transform(_.isDefined))(canvasController.agentCanvas.render),
      showIf(model.subProp(_.recordingSelected).transform(_.isDefined))(canvasController.bigwfCanvas.render),
      showIf(model.subProp(_.recordingSelected).transform(_.isDefined))(canvasController.bigwfCanvas2.render)
    )
  }
}


class RecordingPresenter(model: ModelProperty[RecordingModel],
                         recordings: SeqProperty[RecordingInfo],
                         canvasController: WaveformComp) extends Presenter[RecordingState.type] {


  def onPlayClicked(btn: UdashButton) = {
    // fugly
    // >.get.get
    val id = model.subProp(_.recordingSelected).get.get.id
    model.subProp(_.state).modify{ s => s.copy(dataSource = Some(Playback(id)))}
    model.subProp(_.state).modify{ s => s.copy(isRunning = true) }
    canvasController.fireStateChange
  }


  // TODO: Kinda doesnt work
  def onPauseClicked(btn: UdashButton) = {
    say("Does nothing")
  }


  def onStopClicked(btn: UdashButton) = {
    say("Does nothing")
  }


  def recordingClicked(rec: RecordingInfo) = {
    model.subProp(_.recordingSelected).set(Some(rec))
    model.subProp(_.conf).modify( (c: FullSettings) => c.copy(
      daq = c.daq.copy(
        samplerate = rec.daqSettings.samplerate, segmentLength = rec.daqSettings.segmentLength
      )
    ))
    // model.subProp(_.conf).set( model.subProp(_.conf).get.copy( daq = conf.get.daq.copy( samplerate = rec.daqSettings.samplerate, segmentLength = rec.daqSettings.segmentLength )))
    say(s"set rec selected to ${model.subProp(_.recordingSelected).get}")
  }

  override def handleState(state: RecordingState.type): Unit = {
    Context.serverRpc.getRecordings.onComplete {
      case Success(recs) => recordings.set(recs)
      case Failure(ex) => say(s"get rec state failed with $ex")
    }
  }
}


case object RecordingViewFactory extends ViewFactory[RecordingState.type] {

  override def create(): (View, Presenter[RecordingState.type]) = {

    val recordings = SeqProperty.blank[RecordingInfo]
    val model = ModelProperty( RecordingModel(ProgramState(), FullSettings.default, None) )
    val canvasController = new WaveformComp(model.subProp(_.state), model.subProp(_.conf))

    val presenter = new RecordingPresenter(model, recordings, canvasController)
    val view = new RecordingView(model, presenter, recordings, canvasController)
    (view, presenter)
  }
}

case class RecordingModel(state: ProgramState, conf: FullSettings, recordingSelected: Option[RecordingInfo])
object RecordingModel extends HasModelPropertyCreator[RecordingModel]
