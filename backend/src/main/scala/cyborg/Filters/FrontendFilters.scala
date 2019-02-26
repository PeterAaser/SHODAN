package cyborg

import cats.arrow.FunctionK
import cats.data._
import fs2._
import fs2.concurrent.{ Queue, Signal, SignallingRef, Topic }
import cats.effect.implicits._
import cats.effect.Timer
import cats.effect.concurrent.{ Ref }

import cyborg.wallAvoid.Agent
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

object FrontendFilters {

  // def frontendDownsampler

  def assembleFrontendRenderer = Kleisli[Id, FullSettings, (Int => Int) => Pipe[IO,TaggedSegment,DrawCommand]]{ conf =>

    val canvasPoints        = params.waveformVisualizer.vizLength
    val canvasPointLifetime = 1000.millis
    val pointsNeededPerSec  = (canvasPoints.toDouble/canvasPointLifetime.toSeconds.toDouble).toInt
    val pointsPerDrawcall   = conf.daq.samplerate/pointsNeededPerSec


    /**
      The icky stuff in the middle ensures that there can be no gaps between each
      drawcall.

      For example this:           Becomes this:
      
      4|  |##|  |  |##|            4|  |##|  |  |##|
      3|  |##|  |##|##|            3|  |##|  |##|##|
      2|  |##|  |  |  |            2|  |##|  |##|  |
      1|  |  |  |  |  |            1|  |##|  |##|  |
      0|--|--|##|  |--|------      0|--|##|##|##|--|------
     -1|##|  |##|  |  |           -1|##|##|##|  |  |
     -2|##|  |  |  |  |           -2|##|  |  |  |  |
     -3|##|  |  |  |  |           -3|##|  |  |  |  |
     -4|##|  |  |  |  |           -4|##|  |  |  |  |
    */

    (zoomScaler: Int => Int) => {
      def makeDrawCall(hi: Int, lo: Int) = {
        import cyborg.bonus._
        DrawCommand(zoomScaler(hi).clamp(-50,50), zoomScaler(lo).clamp(-50,50), 0)
      }

      in => in.map(_.data)
      .chunkify
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

}
