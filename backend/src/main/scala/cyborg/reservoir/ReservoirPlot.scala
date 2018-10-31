package cyborg

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Timer
import org.jfree._

object ReservoirPlot {
  /**
    * Plots an array of floats. Note that this currently has no notion
    * of timing, and thus has no dynamic updating.
    */
  def plot(stream: Array[Float]): Unit = {
    val dataSet = new data.time.DynamicTimeSeriesCollection(
      1, stream.length, new data.time.Millisecond())
    dataSet.setTimeBase(new data.time.Millisecond())
    dataSet.addSeries(stream, 0, "Stream")
    val ReservoirChart: chart.JFreeChart =
      chart.ChartFactory.createTimeSeriesChart(
      "SHODAN", "time (undef)", "amplitude", dataSet, true, true, false)

    // (TODO) (thomaav): Find a good way to plot domain (time)
    val plot: chart.plot.XYPlot = ReservoirChart.getXYPlot
    val domain = plot.getDomainAxis
    domain.setAutoRange(true)
    domain.setVisible(false)

    val frame = new chart.ChartFrame("SHODAN", ReservoirChart)
    frame.pack()
    ui.RefineryUtilities.centerFrameOnScreen(frame)
    frame.setVisible(true)

    // (TODO) (thomaav): Fix me to work with input data
    val timer = new Timer(1, new ActionListener {
      def actionPerformed(e: ActionEvent): Unit = {
        dataSet.advanceTime
        val newdater = Array[Float](0.0f)
        dataSet.appendData(newdater)
      }
    })

    timer.start
  }
}
