package cyborg

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Timer
import org.jfree._

import scala.concurrent.duration._

object ReservoirPlot {
  /**
    * Plots an array of floats. Note that this will not currently be
    * able to keep up with a samplerate of 20 000 -- replaying at
    * about 1000 should produce smooth results. Consider this a crude
    * debugging tool.
    */
  def plot(stream: Array[Float], samplerate: Int,
    resolution: FiniteDuration = 0.001.second): Unit = {
    val ticksPerSecond = (1.second/resolution).toInt
    val elementsPerTick = (samplerate/ticksPerSecond)
    val totalTicks = (stream.length/elementsPerTick)
    val plottedTicks = (ticksPerSecond)
    val slidingWindowSize = plottedTicks * elementsPerTick
    var remainingStream = stream.drop(slidingWindowSize)

    val dataSet = new data.time.DynamicTimeSeriesCollection(
      1, slidingWindowSize, new data.time.Millisecond())
    dataSet.setTimeBase(new data.time.Millisecond())
    dataSet.addSeries(stream, 0, "Stream")
    val ReservoirChart: chart.JFreeChart =
      chart.ChartFactory.createTimeSeriesChart(
      "SHODAN", "time (undef)", "amplitude", dataSet, true, true, false)

    val plot: chart.plot.XYPlot = ReservoirChart.getXYPlot
    val domain = plot.getDomainAxis
    domain.setAutoRange(true)
    domain.setVisible(false)

    val frame = new chart.ChartFrame("SHODAN", ReservoirChart)
    frame.pack()
    ui.RefineryUtilities.centerFrameOnScreen(frame)
    frame.setVisible(true)

    val timer = new Timer(resolution.toMillis.toInt, new ActionListener {
      def actionPerformed(e: ActionEvent): Unit = {
        val tickData = remainingStream.take(elementsPerTick)
        remainingStream = remainingStream.drop(elementsPerTick)

        for (dataPoint <- tickData) {
          dataSet.advanceTime
          dataSet.appendData(Array(dataPoint))
        }
      }
    })

    timer.start
  }
}
