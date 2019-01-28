package cyborg

object params {

  object experiment {
    val maxSpikesPerSec = 50
    val minFreq = 0.33
    val maxFreq = 10.0
    val DSPticksPerSecond = 50000
  }

  object webSocket {
    val textPort = 9090
    val dataPort = 9091
    val agentPort = 9092
  }

  object waveformVisualizer {
    val vizHeight: Int = 100
    val vizLength: Int = 200

    val messagesPerSecond: Int = 40
    val pointsPerMessagePerChannel: Int = vizLength/messagesPerSecond
    val pointsPerMessage = pointsPerMessagePerChannel*60

  }

  object Network {
    // val meameIP = "10.20.92.130"
    val meameIP = "0.0.0.0"
    val ip = "0.0.0.0"
    val tcpPort = 12340
    val httpPort = 8888
  }


  object GA {
    def evalFunc: Double => Double = x => x
  }

  // should possibly be dynamically configurable?
  object game {
    val width = 10000.0
    val height = 10000.0
    val speed = 3.0
    val turnRate = 0.001
    val viewPoints = 3
    val maxTurnRate = 0.002

    val sightRange = 3000.0
    val deadZone = 200.0
  }


  object StorageParams {
    import java.nio.file.Paths
    val storageType      = "CSV"
    val workingDirectory = Paths.get(".").toAbsolutePath
    val toplevelPath     = workingDirectory + "/MEAdata/"
  }

  object perturbationTransform {
    val scaleRangeToFreq =
      (experiment.maxFreq - experiment.minFreq) / (game.sightRange - game.deadZone)
    val scaleFreqToRange =
      (game.sightRange - game.deadZone) / (experiment.maxFreq - experiment.minFreq)
  }
}
