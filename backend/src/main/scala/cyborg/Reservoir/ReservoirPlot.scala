package cyborg

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyListener
import java.awt.event.KeyEvent
import java.awt.Color
import javax.swing.Timer
import org.jfree._
import org.jfree.chart.annotations.XYTextAnnotation
import org.jfree.data.time.DynamicTimeSeriesCollection
import org.jfree.data.time.{Second, Millisecond}
import org.jfree.chart.JFreeChart
import org.jfree.chart.ChartFactory

import cyborg.backend.Launcher.ioTimer

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

    // Support having multiple streams of data. Note that it's still
    // possible to interact with TimeSeriesPlot as if it has only one
    // series.
    var series = Array[Array[Float]](slidingWindow)
    var remainingStreams = Array[Array[Float]](remainingStream)

    var dataset = new DynamicTimeSeriesCollection(
      1, slidingWindowSize, new Millisecond())
    dataset.setTimeBase(new Millisecond())
    dataset.addSeries(slidingWindow, 0, "Stream")

    // These are the actual chart and plot that are used to modify
    // what's displayed directly. Not very FP friendly.
    val reservoirChart: JFreeChart =
      ChartFactory.createTimeSeriesChart(
        "SHODAN", "time (undef)", "amplitude", dataset, true, true, false)
    val plot: chart.plot.XYPlot = reservoirChart.getXYPlot
    val frame = new chart.ChartFrame("SHODAN", reservoirChart)

    // Buffer annotation is optional
    var plotCenter = 0.0
    var bufferAnnotation: Option[XYTextAnnotation] = None

    var playing = true
    var timer = new Timer(resolution.toMillis.toInt, new ActionListener {
      def actionPerformed(e: ActionEvent): Unit = {
        updateDataset
      }
    })


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


    def addPauseListener: Unit = {
      frame.addKeyListener(new KeyListener {
        def keyReleased(e: KeyEvent): Unit = { }
        def keyTyped(e: KeyEvent): Unit = { }
        def keyPressed(e: KeyEvent): Unit = {
          if (e.getKeyCode == KeyEvent.VK_P) {
            if (playing) pause else unpause
            playing = !playing
          }
        }
      })
    }


    def updateDataset: Unit = {
      if (remainingStreams(0).length >= elementsPerTick) {
        dataset = new DynamicTimeSeriesCollection(
          series.length, slidingWindowSize, new Millisecond())
        dataset.setTimeBase(new Millisecond())

        for (i <- 0 until series.length) {
          series(i) = series(i).drop(elementsPerTick) ++
            remainingStreams(i).take(elementsPerTick)
          remainingStreams(i) = remainingStreams(i).drop(elementsPerTick)
          dataset.addSeries(series(i), i, "Stream" ++ i.toString)
        }

        plot.setDataset(dataset)
        updateBufferAnnotation
      }
    }


    def addStream(stream: Array[Float]): Int = {
      series :+= stream.take(slidingWindowSize)
      remainingStreams :+= stream.drop(slidingWindowSize)
      series.length - 1
    }


    def extendStream(seriesNumber: Int, stream: Array[Float]): Unit = {
      remainingStreams(seriesNumber) ++= stream
    }


    def updatePlotCenter: Unit = {
      val upperBound = dataset.getDomainUpperBound(false)
      val lowerBound = dataset.getDomainLowerBound(false)
      plotCenter = upperBound / 2 + lowerBound / 2
    }


    def addBufferAnnotation: Unit = {
      // Needed to position annotations correctly, as we are using
      // milliseconds as range
      bufferAnnotation = Some(new XYTextAnnotation(
        remainingStreams(0).length.toString,
        plotCenter,
        PlotConfig.lowerBound + 20.0))
      bufferAnnotation.foreach(plot.addAnnotation(_))
    }


    def updateBufferAnnotation: Unit = {
      updatePlotCenter
      bufferAnnotation.foreach(annotation => {
        annotation.setText("Buffered data available: " ++
          remainingStreams(0).length.toString)
        annotation.setX(plotCenter)
      })
    }


    def ++=(stream: Array[Float]): Unit = {
      extendStream(0, stream)
    }


    def show: Unit = {
      hideDomain
      setRange(minRangeValue, maxRangeValue)
      addPauseListener
      addBufferAnnotation

      frame.pack()
      ui.RefineryUtilities.centerFrameOnScreen(frame)
      frame.setVisible(true)

      timer.start
    }


    def pause: Unit = {
      timer.stop
    }


    def unpause: Unit = {
      timer = new Timer(resolution.toMillis.toInt, new ActionListener {
        def actionPerformed(e: ActionEvent): Unit = {
          updateDataset
        }
      })
      timer.start
    }
  }
}


/**
  * The examples (well, graphing in general) really don't keep up very
  * well with bigger sample rates for now. As real data becomes
  * available for analysis, graphing should be improved to condense
  * the samples in a manner that is expedient.
  */
object PlotExamples {
  import backendImplicits._
  import cats.effect._
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
  def RBNStreamExample: Unit = {
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
