package cyborg

import java.awt.event.KeyListener
import java.awt.event.KeyEvent
import org.graphstream.graph.implementations._
import org.graphstream.graph.Node
import org.graphstream.ui.view._
import cats.effect._

object RBNGraph {
  def initGraph(rbn: RBN): MultiGraph = {
    val graph: MultiGraph = new MultiGraph("RBNGraph")

    // Adding _all_ nodes before we start adding edges is easier
    for (i <- 0 until rbn.state.length) {
      val n: Node = graph.addNode(i.toString)
      n.addAttribute("ui.label", i.toString)
      n.addAttribute("ui.class", rbn.state(i).toString)
    }

    for ((neighbors, i) <- rbn.edges.zipWithIndex) {
      for (neighbor <- neighbors) {
        graph.addEdge(i.toString + '-' + neighbor.toString,
          neighbor.toString, i.toString, true)
        ()
      }
    }

    graph.addAttribute("ui.stylesheet",
      "node.false { fill-color: red; } node.true { fill-color: blue; }")

    // Quality is obviously a more time consuming rendering
    // algorithm -- disable for performance
    graph.addAttribute("ui.quality")
    graph.addAttribute("ui.antialias")

    graph
  }


  def initViewer(graph: MultiGraph, initialRBN: RBN): Unit = {
    var liveRBN = initialRBN
    def updateGraph: Unit = {
      for (i <- 0 until liveRBN.state.length) {
        val node: Node = graph.getNode(i.toString)
        node.changeAttribute("ui.class", liveRBN.state(i).toString)
      }
    }

    val viewer = graph.display
    viewer.getDefaultView.requestFocusInWindow
    viewer.getDefaultView.addKeyListener(new KeyListener {
      def keyReleased(e: KeyEvent): Unit = { }
      def keyTyped(e: KeyEvent): Unit = { }
      def keyPressed(e: KeyEvent): Unit = {
        if (e.getKeyCode == KeyEvent.VK_N) {
          liveRBN.printStateANSI
          liveRBN = liveRBN.step
          updateGraph
        }

        def getTimestampedFP: String = {
          import io.files._
          RBN.resourceDir + "RBN" + ", " + fileIO.getTimeStringUnsafe
        }

        if (e.getKeyCode == KeyEvent.VK_S) {
          // There is probably some way to save directly with
          // getResource.
          RBN.serialize(liveRBN, getTimestampedFP)
        }

        // Ignore this for now -- implement loading for viz when
        // needed (if ever).
        if (e.getKeyCode == KeyEvent.VK_L) {
          // If we are already in RBNGraph, we don't really care
          // much about the unsafeRunSync anymore.
          // (for {
          //   rbn <- RBN.deserialize(getTimestampedFP)
          // } yield (liveRBN = rbn)).unsafeRunSync()
        }
      }
    })
  }


  def run(rbn: RBN): Unit = {
    val graph = initGraph(rbn)
    initViewer(graph, rbn)
  }
}

/**
  * The examples (well, graphing in general) really don't keep up very
  * well with bigger sample rates for now. As real data becomes
  * available for analysis, graphing should be improved to condense
  * the samples in a manner that is expedient.
  */
object RBNGraphExamples {
  import backendImplicits._
  import cyborg.io.files._
  import java.nio.file.Paths
  import scala.concurrent.duration._
  import utilz._

  val segmentLength = 2000
  val channels = 60
  val samplerate = 500
  val channelPath = params.StorageParams.toplevelPath + "19.04.2018, 11:22:31"
  val channelStream = fileIO.readCSV[IO](Paths.get(
    channelPath
  ))
    .through(singleChannelPipe(0, channels, segmentLength))
    .through(naiveDecimationPipe(4))


  /**
    * This goes through a naiveDecimationPipe, as the currently used
    * input data is given in series of four of the same value.
    */
  def channelOutputExample: Unit = {
    channelStream
      .through(attachVizPipe(samplerate, 0.1.second))
      .compile.drain.unsafeRunSync()
  }


  /**
    * (TODO): Fix this to pad correctly, as it is offset as time
    * passes since the replication padding in unsafeSpikeDetector is
    * _wrong_.
    */
  def spikeDetectorExample: Unit = {
    val threshold = 100
    val spikes = channelStream.
      through(spikeDetector.unsafeSpikeDetector(
        samplerate, threshold, replicate = true))
    val zipped = flatRoundRobinL[IO,Int](List(channelStream, spikes))
    zipped.stream
      .through(attachVizPipe(samplerate, 0.1.second, zipped.numStreams))
      .compile.drain.unsafeRunSync()
  }


  /**
    * RBNs will output sawtooth waves, so they fit as a simple
    * example.
    */
  def RBNExample: Unit = {
    import RBN._
    import RBNGen._
    import RBNStreams._

    val samplerate = 500
    val stream = ActiveRBNs.randomRBN
      .outputNodeState[IO](0, samplerate, 0.1.second)
    stream.through(attachVizPipe[IO](samplerate, 0.1.second))
      .compile.drain.unsafeRunSync()
  }
}
