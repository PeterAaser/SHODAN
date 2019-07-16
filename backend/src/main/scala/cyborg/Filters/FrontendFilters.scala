package cyborg

import cats.arrow.FunctionK
import cats.data._
import fs2._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.WallAvoid.Agent
import _root_.io.udash.rpc.ClientId
import java.nio.file.Paths
import org.joda.time.Seconds
import scala.language.higherKinds
import cats.effect.IO
import cats.effect._
import cats._
import cats.implicits._

import cyborg.backend.server.ApplicationServer
import cyborg.Settings._
import cyborg.utilz._
import cyborg.State._

import scala.concurrent.duration._

import backendImplicits._
import RPCmessages._

import org.http4s.client.blaze._
import org.http4s.client._

class FrontendFilters(conf: FullSettings, vizState: VizState, spikeTools: SpikeTools[IO]) {

  val canvasPoints        = params.waveformVisualizer.vizLength
  val canvasPointLifetime = timeCompressionLevelToTime(vizState.timeCompressionLevel)
  val pointsNeededPerSec  = (canvasPoints.toDouble/(canvasPointLifetime.toMillis.toDouble/1000.0)).toInt
  val pointsPerDrawcall   = conf.daq.samplerate/pointsNeededPerSec

  say(s"New frontend filter constructed")
  say(s"Span in milliseconds: ${canvasPointLifetime.toMillis.toInt}ms (i.e how long each datapoint is visible)")
  say(s"In order to fill the demand, $pointsNeededPerSec points are needed per second")
  say(s"points per drawcall: $pointsPerDrawcall (i.e how many points of data per pixelwide column)")
  say(s"DAQ samplerate is ${conf.daq.samplerate} btw")

  val renderer: Pipe[IO,Chunk[Int], DrawCommand] = {

    /**
      * The icky stuff in the middle ensures that there can be no gaps between each
      * drawcall.

      *     For example this:           Becomes this:
      *     
      *      4|  |##|  |  |##|            4|  |##|  |  |##|
      *      3|  |##|  |##|##|            3|  |##|  |##|##|
      *      2|  |##|  |  |  |            2|  |##|  |##|  |
      *      1|  |  |  |  |  |            1|  |##|  |##|  |
      *      0|--|--|##|  |--|------      0|--|##|##|##|--|------
      *     -1|##|  |##|  |  |           -1|##|##|##|  |  |
      *     -2|##|  |  |  |  |           -2|##|  |  |  |  |
      *     -3|##|  |  |  |  |           -3|##|  |  |  |  |
      *     -4|##|  |  |  |  |           -4|##|  |  |  |  |
      */

    val zoomScaler = zoomFilter(vizState.zoomLevel)
    def makeDrawCall(hi: Int, lo: Int) = {
      import cyborg.bonus._
      DrawCommand(zoomScaler(hi).clamp(-50,50), zoomScaler(lo).clamp(-50,50), 0)
    }

    in => in.hideChunks
      .through(downsampleHiLoWith(pointsPerDrawcall,makeDrawCall))
      .scanChunks((Int.MinValue, Int.MaxValue)){
        case(acc, chunk) => {
          val buf = Array.ofDim[DrawCommand](chunk.size)
          var prevMax = acc._1
          var prevMin = acc._2

          for(ii <- 0 until chunk.size){
            buf(ii) = chunk(ii).copy(
              yMax = if(chunk(ii).yMax < prevMin) prevMin else chunk(ii).yMax,
              yMin = if(chunk(ii).yMin > prevMax) prevMax else chunk(ii).yMin)

            prevMax = chunk(ii).yMax
            prevMin = chunk(ii).yMin
          }
          ((prevMin, prevMin), Chunk.seq(buf))
        }
      }
  }


  /**
    * Create the datastream for rendering all 60 channels.
    * This datastream is used by the frontend to populate the grid waveform visualizer
    */
  def renderAll(topics: Seq[Topic[IO,Chunk[Int]]]): Stream[IO,Array[Array[DrawCommand]]] = {

    // Should probably be closer to All of them
    val pixelsPerPull = pointsNeededPerSec

    // Yep, a mutable reference to a mutable collection.
    var buf = Array.ofDim[DrawCommand](pixelsPerPull*60)


    wakeUp(topics).flatMap{ (sl: Chunk[Stream[IO,Chunk[Int]]]) =>
      val streams = sl.toArray.map(_.through(this.renderer))
      def go(idx: Int): Pull[IO,Array[DrawCommand],Unit] = {
        streams(idx).pull.unconsN(pixelsPerPull, false).flatMap{
          case Some((chunk, tl)) => {
            chunk.copyToArray(buf, pixelsPerPull*idx)
            streams(idx) = tl
            if(idx == 59){
              for {
                _ <- Pull.output1(buf)
                _ <- Pull.eval(IO { buf = Array.ofDim[DrawCommand](pixelsPerPull*60) } )
                _ <- go(0)
              } yield ()
            }
            else
              go(idx+1)
          }
          case None => Pull.doneWith("Channel broadcaster deaded")
        }
      }
      go(0).stream
        .map(Array(_))
      // .map{x => say(s"outputting ${x(0).size} pixels", timestamp = true); x}

    }
  }


  import cyborg.RPCmessages.DrawCommand
  def visualizeRaw(hi: Int, lo: Int) = DrawCommand(hi, lo, 0)
  def visualizeAvg(hi: Int, lo: Int) = DrawCommand(hi, lo, 1)
  


  /**
    * Not 100% sure if the description is correct. Sorry
    * 
    * Visualizes the raw datastream with the moving average superimposed.
    * Used to tune the paratemers of the averaging function.
    */
  import cyborg.RPCmessages._
  def visualizeRawAvg: Pipe[IO, Chunk[Int], Array[Array[DrawCommand]]] = { inputs =>
    Stream.eval(Queue.unbounded[IO,Chunk[Int]]).flatMap{ q1 =>
      Stream.eval(Queue.unbounded[IO,Chunk[Int]]).flatMap{ q2 =>

        val ins = inputs
          .observe(q1.enqueue)
          .through(q2.enqueue)

        val rawStream = q1.dequeue.hideChunks.drop((spikeTools.kernelSize/2) + 1)
        val rawStreamViz = rawStream
          .through(downsampleHiLoWith(pointsPerDrawcall, visualizeRaw))

        val blurred = q2.dequeue.hideChunks.through(spikeTools.gaussianBlurConvolutor)
        val blurredViz = blurred
          .through(downsampleHiLoWith(pointsPerDrawcall, visualizeAvg))

        ((rawStreamViz zip blurredViz).map{ case(a,b) =>
          Array(a,b)
        }.mapN(_.toArray, pointsNeededPerSec))
          .concurrently(ins)
      }
    }
  }


  /**
    * Visualizes the frequency of spikes on all channels as a conveyor
    */
  def visualizeAllSpikes(topics: List[Topic[IO,Chunk[Int]]]): Stream[IO,Array[Array[DrawCommand]]] = {
    wakeUp(topics).flatMap{ sl =>
      val spikes = spikeTools.windowedSpikes(sl.toList)
      spikes.map(_.map(freq => DrawCommand(freq,freq,freq)).toArray)
        .map(Array(_))
    }
  }


  def visualizeNormSpikes: Pipe[IO,Chunk[Int],Array[Array[DrawCommand]]] = { inputs =>
    Stream.eval(Queue.unbounded[IO,Chunk[Int]]).flatMap{ q1 =>
      Stream.eval(Queue.unbounded[IO,Chunk[Int]]).flatMap{ q2 =>
        Stream.eval(Queue.unbounded[IO,Chunk[Int]]).flatMap{ q3 =>
          Stream.eval(Queue.unbounded[IO,Chunk[Int]]).flatMap{ q4 =>

            val ins = inputs
              .observe(q1.enqueue)
              .through(q2.enqueue)

            val raw           = q2.dequeue.hideChunks
            val blurred       = q1.dequeue.hideChunks.through(spikeTools.gaussianBlurConvolutor)
            val normalizedIns = spikeTools.avgNormalizer(raw, blurred).chunks
              .observe(q3.enqueue)
              .through(q4.enqueue)

            val normalizedViz = q3.dequeue.hideChunks
              .through(downsampleHiLoWith(pointsPerDrawcall, visualizeRaw))

            val spikes    = q4.dequeue.hideChunks.through(spikeTools.spikeDetectorPipe)
            val spikesViz = spikes.through(downsampleWith(pointsPerDrawcall, 1)((c: Chunk[Boolean]) => c.foldLeft(false)(_||_)))
              .map(b => if(b) DrawCommand(-2600, -10000, 1) else DrawCommand(0,0,0))

            ((normalizedViz zip spikesViz).map{ case(a,b) =>
              Array(a,b)
            }.mapN(_.toArray, pointsNeededPerSec))
              .concurrently(ins)
              .concurrently(normalizedIns)
          }
        }
      }
    }
  }



  def zoomFilter(zoomLevel: Int): Int => Int = zoomLevel match {
    case 0 => (x: Int) => x           // 1µV
    case 1 => (x: Int) => x/2         // 2µV
    case 2 => (x: Int) => x/5         // 5µV
    case 3 => (x: Int) => x/10        // 10µV
    case 4 => (x: Int) => x/20        // 20µV
    case 5 => (x: Int) => x/50        // 50µV
    case 6 => (x: Int) => x/100       // 100µV
    case 7 => (x: Int) => x/200       // 200µV
    case 8 => (x: Int) => x/500       // 500µV
    case 9 => (x: Int) => x/1000      // 1mV
    case 10 => (x: Int) => x/2000     // 2mV
    case 11 => (x: Int) => x/3000     // 3mV
    case 12 => (x: Int) => x/4000     // 4mV
    case 13 => (x: Int) => x/5000     // 5mV
    case 14 => (x: Int) => x/10000    // 10mV
    case 15 => (x: Int) => x/20000    // 20mV
    case 16 => (x: Int) => x/50000    // 50mV
    case 17 => (x: Int) => x/100000   // 100mV
    case 18 => (x: Int) => x/200000   // 200mV
    case 19 => (x: Int) => x/500000   // 500mV
    case 20 => (x: Int) => x/1000000  // 1V
    case 21 => (x: Int) => x/2000000  // 2V
    case 22 => (x: Int) => x/3000000  // 3V
    case _  => (x: Int) => x          // Uhh...?
  }

  def timeCompressionLevelToTime(level: Int): FiniteDuration = level match {
    case 0 => 500.millis
    case 1 => 1.second
    case 2 => 2.second
    case 3 => 3.second
    case 4 => 5.second
    case 5 => 10.second
    case 6 => 20.second
    case 7 => 30.second
  }
}
