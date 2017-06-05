package com.cyborg

import fs2._
import frontendImplicits._
import wallAvoid.Agent
import org.scalajs.dom.html

object frontIO {

  /**
    I AM SHODAN
    */
  def startSHODAN(): Unit = {
    println("sending start SHODAN http")
    frontHTTPclient.startShodanServer.unsafeRunAsync(_ => () )
  }


  def startAgent(): Unit = {
    println("sending start Agent http")
    frontHTTPclient.startAgentServer.unsafeRunAsync(_ => () )
  }


  def startWF(): Unit = {
    println("sending start WF http")
    frontHTTPclient.startWfServer.unsafeRunAsync(_ => () )
  }

  /**
    Opens a websocket to get the hottest new Agent data
    */
  def startAgentStream(cantvas: html.Canvas) = {
    val visualizerSink: Sink[Task,Agent] = Visualizer.visualizerControlSink[Task](cantvas)

    println("Creating agent visualizer")
    val queueTask = fs2.async.unboundedQueue[Task,Agent]
    val request = Stream.eval(queueTask) flatMap { queue =>

      val wsInStream = websocketStream.createAgentWsQueue(queue)
      Stream.eval(wsInStream).mergeDrainL(queue.dequeue.through(visualizerSink))
    }
    request.run.unsafeRunAsync( _ => () )
  }


  def startWaveformStream(cantvas: html.Canvas) = {

    val controller = new waveformVisualizer.WFVisualizerControl(cantvas)

    println("Creating wf visualizer")
    val queueTask = fs2.async.unboundedQueue[Task,Vector[Int]]
    val request: Stream[Task,Unit] = Stream.eval(queueTask) flatMap { queue =>

      val wsInStream = websocketStream.createWaveformWsQueue(queue)

      val channelStreams = utilz.alternator(queue.dequeue, 4, 60, 1000)
      val mapped =
        channelStreams.flatMap(streams =>
          controller.gogo[Task](streams.map(_.through(utilz.chunkify)).toList))

        Stream.eval(wsInStream) merge mapped
    }
    request.run.unsafeRunAsync( _ => () )
  }
}
