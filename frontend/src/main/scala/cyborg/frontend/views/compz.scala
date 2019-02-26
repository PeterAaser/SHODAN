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

  // A single pixels worth of drawing
  // Takes on a different meaning for the inner array holds a big chunk of data rather than
  // several calls per pixel

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

  type DrawCall = Array[DrawCommand]

  val wfCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
  val agentCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]


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

  def fireStateChange: Unit = {
    val _ = Context.serverRpc.setSHODANstate(state.get)
  }

  val agentQueue = new scala.collection.mutable.Queue[Agent]()
  val confQueue  = new scala.collection.mutable.Queue[FullSettings]()
  val stateQueue = new scala.collection.mutable.Queue[ProgramState]()

  // The queue for receiving unMuxed draw calls
  val drawQueue  = new scala.collection.mutable.Queue[(Int, Array[DrawCall])]()

  // Special queue for all waveforms
  val wfQueue    = new scala.collection.mutable.Queue[Array[DrawCommand]]()


  // Queues for the various canvases
  val canvasQueues = new scala.collection.mutable.HashMap[Int, scala.collection.mutable.Queue[Array[DrawCall]]]()
  val myQueue = new scala.collection.mutable.Queue[Array[DrawCall]]()
  canvasQueues(1) = myQueue

  val myQueue2 = new scala.collection.mutable.Queue[Array[DrawCall]]()
  canvasQueues(2) = myQueue2

  val bigwfCanvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
  val bigwfCanvas2: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]

  def handleDrawcallBatch(idx: Int, dcs: Array[DrawCall]): Unit = {
    if (idx == 0)
      wfQueue.enqueue(dcs(0))
    else
      canvasQueues.lift(idx).foreach(q => q += dcs)
  }


  agentQueue.enqueue(Agent.init)

  cyborg.frontend.services.rpc.Hurr.register(
    agentQueue,
    confQueue,
    stateQueue,
    handleDrawcallBatch)


  def onChannelClicked(c: Int) = {
    val _ = Context.serverRpc.selectLargeChannel(c)
  }

  val wf = new cyborg.WFVisualizerControl(wfCanvas, wfQueue, onChannelClicked)
  val ag = new cyborg.Visualizer.VisualizerControl(agentCanvas, agentQueue)
  val big = new cyborg.LargeWFviz(bigwfCanvas, myQueue)
  val big2 = new cyborg.LargeWFviz(bigwfCanvas2, myQueue2)

  def onStopClicked(btn: UdashButton) = state.modify{ s =>
    s.copy(isRunning = false, isRecording = false)
  }

  def onStopRecordingClicked(btn: UdashButton) = state.modify{ s =>
    s.copy(isRecording = false)
  }

  def onRangeUpClicked(btn: UdashButton) = {
    Context.serverRpc.setDownscalingFactor(1).onComplete(x => say("OK"))
  }

  def onRangeDownClicked(btn: UdashButton) = {
    Context.serverRpc.setDownscalingFactor(-1).onComplete(x => say("OK"))
  }
}
