package cyborg

import org.graphstream.graph.implementations._
import org.graphstream.graph.Node
import org.graphstream.ui.view._

object ReservoirGraph {
  /**
    * Basic listener to respond to events within a graph viewer. This
    * is implemented to be able to step a reservoir continuously.
    */
  class GraphListener(val graph: MultiGraph, var rbn: RBNContext.RBN)
      extends ViewerListener {
    def updateGraph: Unit = {
      for (i <- 0 until rbn.state.length) {
        val node: Node = graph.getNode(i.toString)
        node.changeAttribute("ui.class", rbn.state(i).toString)
      }
    }

    def viewClosed(x: String): Unit = { }
    def buttonPushed(x: String): Unit = { rbn = rbn.step; updateGraph }
    def buttonReleased(x: String): Unit = { }
  }

  def initGraph(rbn: RBNContext.RBN): MultiGraph = {
    val graph: MultiGraph = new MultiGraph("RBNGraph")

    // Adding _all_ nodes before we start adding edges is easier
    for (i <- 0 until rbn.state.length) {
      val n: Node = graph.addNode(i.toString)
      n.addAttribute("ui.label", i.toString)
      n.addAttribute("ui.class", rbn.state(i).toString)
    }

    for ((neighbors, i) <- rbn.edges.zipWithIndex) {
      for (neighbor <- neighbors) {
        graph.addEdge(i.toString + neighbor.toString,
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

  def run(rbn: RBNContext.RBN): Unit = {
    val graph = initGraph(rbn)

    val viewer: Viewer = graph.display
    val viewerPipe: ViewerPipe = viewer.newViewerPipe
    viewerPipe.addViewerListener(new GraphListener(graph, rbn))

    while (true) {
      viewerPipe.pump
    }
  }
}
