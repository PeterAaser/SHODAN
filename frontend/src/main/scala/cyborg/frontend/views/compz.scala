package cyborg.frontend

import cyborg.frontend.routing._
import io.udash._
import io.udash.bindings.modifiers.Binding
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.utils.Icons
import org.scalajs.dom.raw.Node
import scala.util.{ Failure, Success }


import cyborg._
import cyborg.RPCmessages._
import frontilz._

import io.udash.css._

import org.scalajs.dom.document
import scalatags.JsDom.all._
import org.scalajs.dom.html.Canvas
import io.udash.bootstrap.button._

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalajs.dom.document
import org.scalajs.dom.html

object compz {

  /**
    * There has to be better API for this
    */
  def renderDBrecord(r: ReadableProperty[Option[RecordingInfo]]): Binding = {
    produce(r)(r => renderDBrecordOpt(r))
  }


  def renderDBrecordOpt(rec: Option[RecordingInfo]): Seq[Node] = {
    val huh = rec.map{ r =>
      ul(
        li(p( s"Date: ${r.date}" )),
        li(r.duration.map( x => p(s"Duration: $x")).getOrElse(p("Duration missing!"))),
        li(p( s"samplerate: ${r.daqSettings.samplerate}" )),
        li(p( s"segment length ${r.daqSettings.segmentLength}" )),
        li(p( s"comment: ${r.comment }" )),
      ).render
    }
    huh.toList
  }

  def renderDBrecordSmall(rp: Property[RecordingInfo]): org.scalajs.dom.html.Paragraph = {
    def renderDuration(d: Option[String]) = d.getOrElse("UNKNOWN")
    val r = rp.get
    p(s"recording date: ${r.date}, duration: ${renderDuration(r.duration)}").render
  }
}


/**
  Create the queues and register the necessary canvases, queues and registrations.
  Let's try this without the models for starters

  TODO: Come up with a better name plz
  */
import cyborg.State._
import cyborg.Settings._
import cyborg.wallAvoid.Agent
class WaveformComp(state: Property[ProgramState], conf: Property[FullSettings]) {

  val wfCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
  val agentCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
  val channelCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]


  /**
    This is kinda very very bad...
    */
  scalajs.js.timers.setInterval(1000){
    say("checking for updates")
    if(confQueue.size > 0){
      val newest: FullSettings = confQueue.dequeueAll(_ => true).last
      conf.set(newest)
      say(s"Updated settings to $newest yo")
    }

    if(stateQueue.size > 0){
      val newest: ProgramState = stateQueue.dequeueAll(_ => true).last
      state.set(newest)
      say(s"Updated conf to $newest yo")
    }
  }

  def fireStateChange: Unit = Context.serverRpc.setSHODANstate(state.get)

  val wfQueue    = new scala.collection.mutable.Queue[Array[Int]]()
  val agentQueue = new scala.collection.mutable.Queue[Agent]()
  val confQueue  = new scala.collection.mutable.Queue[FullSettings]()
  val stateQueue = new scala.collection.mutable.Queue[ProgramState]()
  val drawQueue  = new scala.collection.mutable.Queue[Array[Array[DrawCommand]]]()
  
  agentQueue.enqueue(Agent.init)

  cyborg.frontend.services.rpc.Hurr.register(
    agentQueue,
    wfQueue,
    confQueue,
    stateQueue,
    drawQueue
  )

  val wf = new cyborg.waveformVisualizer.WFVisualizerControl(wfCanvas, wfQueue)
  val ag = new cyborg.Visualizer.VisualizerControl(agentCanvas, agentQueue)
  val big = new cyborg.LargeWFviz(channelCanvas, drawQueue)

  def onStopClicked(btn: UdashButton) = state.modify{ s =>
    s.copy(isRunning = false, isRecording = false)
  }

  def onStopRecordingClicked(btn: UdashButton) = state.modify{ s =>
    s.copy(isRecording = false)
  }

  def onRangeUpClicked(btn: UdashButton) = {
    val current = wf.getMaxVal
    if(current > 128)
      wf.setMaxVal(current/2)
  }

  def onRangeDownClicked(btn: UdashButton) = {
    val current = wf.getMaxVal
    // if(current <= 6400000)
      wf.setMaxVal(current*2)
  }
}
