package cyborg

import java.awt.event.KeyListener
import java.awt.event.KeyEvent
import org.graphstream.graph.implementations._
import org.graphstream.graph.Node
import org.graphstream.ui.view._

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
          liveRBN = liveRBN.step
          updateGraph
        }
      }
    })
  }


  def run(rbn: RBN): Unit = {
    val graph = initGraph(rbn)
    initViewer(graph, rbn)
  }
}
