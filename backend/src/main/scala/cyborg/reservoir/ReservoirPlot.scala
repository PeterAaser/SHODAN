package cyborg

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Timer
import org.jfree._

import scala.concurrent.duration._

object ReservoirPlot {
  object PlotConfig {
    val visibleTicks = 3
    val lowerBound = -500
    val upperBound = 500
  }

  /**
   * Helper function for pre-calculating the size of the sliding
   * window _before_ actually initializing a plot. Useful for
   * initializing a stream of zero-values to initialize
   * TimeSeriesPlot.
   */
  def getSlidingWindowSize(samplerate: Int,
    resolution: FiniteDuration = 0.5.second): Int = {
    val ticksPerSecond = (1.second/resolution).toInt
    val elementsPerTick = (samplerate/ticksPerSecond)
    val plottedTicks = ticksPerSecond*PlotConfig.visibleTicks
    val slidingWindowSize = plottedTicks * elementsPerTick

    slidingWindowSize
  }

  /**
    * Plots a stream. Consider this a crude debugging tool.
    */
  class TimeSeriesPlot(stream: Array[Float], samplerate: Int,
    resolution: FiniteDuration = 0.5.second) {
    val ticksPerSecond = (1.second/resolution).toInt
    val elementsPerTick = (samplerate/ticksPerSecond)
    val plottedTicks = ticksPerSecond*PlotConfig.visibleTicks
    val slidingWindowSize = plottedTicks * elementsPerTick
    var minRangeValue = PlotConfig.lowerBound
    var maxRangeValue = PlotConfig.upperBound

    // The sliding window ticks over the stream according to the
    // variables above.
    var slidingWindow = stream.take(slidingWindowSize)
    var remainingStream = stream.drop(slidingWindowSize)

    var dataset = new data.time.DynamicTimeSeriesCollection(
      1, slidingWindowSize, new data.time.Millisecond())
    dataset.setTimeBase(new data.time.Millisecond())
    dataset.addSeries(slidingWindow, 0, "Stream")

    // These are the actual chart and plot that are used to modify
    // what's displayed directly. Not very FP friendly.
    val reservoirChart: chart.JFreeChart =
      chart.ChartFactory.createTimeSeriesChart(
        "SHODAN", "time (undef)", "amplitude", dataset, true, true, false)
    val plot: chart.plot.XYPlot = reservoirChart.getXYPlot
    val frame = new chart.ChartFrame("SHODAN", reservoirChart)

    def hideDomain: Unit = {
      val domain = plot.getDomainAxis
      domain.setAutoRange(true)
      domain.setVisible(false)
    }

    def setRange(min: Int, max: Int): Unit = {
      minRangeValue = min
      maxRangeValue = max
      val range = plot.getRangeAxis
      range.setRange(minRangeValue, maxRangeValue)
    }

    def updateDataset: Unit = {
      slidingWindow = slidingWindow.drop(elementsPerTick) ++
            remainingStream.take(elementsPerTick)
      remainingStream = remainingStream.drop(elementsPerTick)

      dataset = new data.time.DynamicTimeSeriesCollection(
        1, slidingWindowSize, new data.time.Millisecond())
      dataset.setTimeBase(new data.time.Millisecond())
      dataset.addSeries(slidingWindow, 0, "Stream")

      plot.setDataset(dataset)
    }

    def show: Unit = {
      hideDomain
      setRange(minRangeValue, maxRangeValue)

      frame.pack()
      ui.RefineryUtilities.centerFrameOnScreen(frame)
      frame.setVisible(true)

      val timer = new Timer(resolution.toMillis.toInt, new ActionListener {
        def actionPerformed(e: ActionEvent): Unit = {
          updateDataset
        }
      })

      timer.start
    }
  }
}
