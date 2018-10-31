package cyborg

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
    val range = plot.getDomainAxis
    range.setVisible(false)

    val frame = new chart.ChartFrame("Reservoir", ReservoirChart)
    frame.pack()
    frame.setVisible(true)
  }
}
