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

  object http {
    // object MEAMEclient {
    //   val ip = "129.241.201.110"
    //   val port = 8888 // we're not an open server, so we don't use the regular http port.
    // }

    // mock version
    object MEAMEclient {
      val ip = "0.0.0.0"
      val port = 8888
    }

    object SHODANserver {
      val SHODANserverIP = "127.0.0.1"
      val SHODANserverPort = 9998
    }
  }

  object TCP {
    val ip = "0.0.0.0"
    val port = 12340
    val sawtooth = 12341
    val sendBufSize = 4096        // rather low bandwidth required for stimreqs
    val recvBufSize = 262144      // 262144 = 1024*256, 256kb, matches kernel tcp socket buffer size
    val format = "JVM"
  }

  // object TCP {
  //   val ip = "129.241.201.110"
  //   val port = 12340
  //   val sawtooth = 12341
  //   val sendBufSize = 4096        // rather low bandwidth required for stimreqs
  //   val recvBufSize = 262144      // 262144 = 1024*256, 256kb, matches kernel tcp socket buffer size
  //   val format = "Csharp"
  // }


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
