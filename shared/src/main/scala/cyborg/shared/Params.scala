package cyborg

import scala.concurrent.duration._
object params {

  // TODO: Anachronism, should be moved
  object experiment {
    val maxSpikesPerSec = 50
    val DSPticksPerSecond = 50000
  }


  object waveformVisualizer {
    val vizHeight: Int = 100
    val vizLength: Int = 200

    val messagesPerSecond: Int = 40
    val pointsPerMessagePerChannel: Int = vizLength/messagesPerSecond
    val pointsPerMessage = pointsPerMessagePerChannel*60
  }

  object staticFilterParams {
    val kernelWidts = 30.millis
  }

  object Network {
    // val mock = true
    val mock = false
    val meameIP = if(mock) "0.0.0.0" else "10.20.92.130"
    val tcpPort = 12340
    val httpPort = 8888
  }


  object GA {
    def evalFunc: Double => Double = x => x
  }

  // should possibly be dynamically configurable?
  // object game {
  //   val width = 10000.0
  //   val height = 10000.0

  //   // Distance covered per tick
  //   val speed = 1.0

  //   // Readout output turn multiplier
  //   val turnRate = 0.0025
  //   val viewPoints = 2
  //   val maxTurnRate = 0.00025

  //   val sightRange = 3000.0
  //   val deadZone = 200.0
  // }

  object game {
    val width = 10000.0
    val height = 10000.0

    // Distance covered per tick
    val speed = 40.0

    // Readout output turn multiplier
    val turnRate = 0.05
    val viewPoints = 2
    val maxTurnRate = 0.005

    val sightRange = 3000.0
    val deadZone = 200.0
  }


  object StorageParams {
    import java.nio.file.Paths
    val storageType      = "CSV"
    val workingDirectory = Paths.get(".").toAbsolutePath
    val toplevelPath     = workingDirectory + "/MEAdata/"
  }

}
