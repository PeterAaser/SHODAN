package cyborg

import org.graphstream.graph.implementations._
import org.graphstream.graph.{Node => gNode}
import org.graphstream.ui.view._

object ReservoirGraph {
  /**
    * Basic listener to respond to events within a graph viewer. This
    * is implemented to be able to step a reservoir continuously.
    */
  class GraphListener(val g: MultiGraph) extends ViewerListener {
    def viewClosed(x: String) = { println("View closed") }
    def buttonPushed(x: String) = { println("Button push") }
    def buttonReleased(x: String) = { println("Button released") }
  }

  /**
    * Display an RBN using GraphStream (experimental -- use at your
    * own risk).
    */
  def display(rbn: RBNContext.RBN): MultiGraph = {
    val graph: MultiGraph = new MultiGraph("RBNGraph")

    // Adding _all_ nodes before we start adding edges is easier
    for (i <- 0 until rbn.state.length) {
      val n: gNode = graph.addNode(i.toString)
      n.addAttribute("ui.label", i.toString)
      n.addAttribute("ui.class", rbn.state(i).toString)
      ()
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

    val viewer: Viewer = graph.display
    val viewerPipe: ViewerPipe = viewer.newViewerPipe()
    viewerPipe.addViewerListener(new GraphListener(graph))

    while (true) {
      viewerPipe.pump
    }

    graph
  }
}
