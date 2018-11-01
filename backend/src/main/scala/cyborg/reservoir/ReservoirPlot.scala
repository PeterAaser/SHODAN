package cyborg

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Timer
import org.jfree._

import scala.concurrent.duration._

object ReservoirPlot {
  /**
    * Plots an array of floats. Consider this a crude debugging tool.
    */
  def plot(stream: Array[Float], samplerate: Int,
    resolution: FiniteDuration = 0.5.second): Unit = {
    val ticksPerSecond = (1.second/resolution).toInt
    val elementsPerTick = (samplerate/ticksPerSecond)
    val plottedTicks = ticksPerSecond*3
    val slidingWindowSize = plottedTicks * elementsPerTick
    var slidingWindow = stream.take(slidingWindowSize)
    var remainingStream = stream.drop(slidingWindowSize)

    var dataSet = new data.time.DynamicTimeSeriesCollection(
      1, slidingWindowSize, new data.time.Millisecond())
    dataSet.setTimeBase(new data.time.Millisecond())
    dataSet.addSeries(slidingWindow, 0, "Stream")
    val reservoirChart: chart.JFreeChart =
      chart.ChartFactory.createTimeSeriesChart(
      "SHODAN", "time (undef)", "amplitude", dataSet, true, true, false)

    val plot: chart.plot.XYPlot = reservoirChart.getXYPlot
    val domain = plot.getDomainAxis
    domain.setAutoRange(true)
    domain.setVisible(false)

    val frame = new chart.ChartFrame("SHODAN", reservoirChart)
    frame.pack()
    ui.RefineryUtilities.centerFrameOnScreen(frame)
    frame.setVisible(true)

    val timer = new Timer(resolution.toMillis.toInt, new ActionListener {
      def actionPerformed(e: ActionEvent): Unit = {
        slidingWindow = slidingWindow.drop(elementsPerTick) ++ remainingStream.take(elementsPerTick)
        remainingStream = remainingStream.drop(elementsPerTick)

        dataSet = new data.time.DynamicTimeSeriesCollection(
          1, slidingWindowSize, new data.time.Millisecond())
        dataSet.setTimeBase(new data.time.Millisecond())
        dataSet.addSeries(slidingWindow, 0, "Stream")

        plot.setDataset(dataSet)
      }
    })

    timer.start
  }
}
