package cyborg


object params {

  object experiment {
    val totalChannels = 60
    val samplerate = 1000
    val segmentLength = 100
    val maxSpikesPerSec = 50

    val minFreq = 0.33
    val maxFreq = 10.0

    def printMe(): Unit = {
      println("----[Experiment params]----")
      println(s"samplerate: \t\t$samplerate")
      println(s"segment length: \t\t$segmentLength")
      println(s"max spikes per second: \t\t$maxSpikesPerSec")
      println(s"refactory period for spike detector pipe: ${samplerate/maxSpikesPerSec}")
    }
  }

  object webSocket {
    val textPort = 9090
    val dataPort = 9091
    val agentPort = 9092
  }

  object ANN {
    val weightMin = -2.0
    val weightMax =  2.0
  }

  object http {
    object MEAMEclient {
      val ip = "129.241.201.110"
      val port = "8888" // we're not an open server, so we don't use the regular http port.
    }

    object SHODANserver {
      val SHODANserverIP = "127.0.0.1"
      val SHODANserverPort = 9998
    }
  }

  object TCP {
    val ip = "129.241.201.110"
    val port = 12340
    val sendBufSize = 4096        // rather low bandwidth required for stimreqs
    val recvBufSize = 262144      // 262144 = 1024*256, 256kb, matches kernel tcp socket buffer size
  }


  object filtering {

    val MAGIC_THRESHOLD = 1000
    val layout = List(2,3,2) // Layout of the neural network
    val movingAverageRetention = 10 // should depend on samplerate

    val spikeCooldown = experiment.samplerate/experiment.maxSpikesPerSec


    def printNetworkLayout(): Unit = {
      layout.foreach { λ => print(s"[$λ]") }
      println()
    }

    def printMe(): Unit = {
      println("----[Filtering params]----")
      println(s"Spike thresholding value is set to $MAGIC_THRESHOLD")
      println(s"The current neural network layout is: ")
      printNetworkLayout()
      println(s"The input of to the neural network is the moving average of the last $movingAverageRetention spikes")
      println(s"the 'cooldown' in samples between each possible spike is $spikeCooldown")
    }

  }

  object GA {
    val pipesPerGeneration = 6
    val newPipesPerGeneration = 2
    val newMutantsPerGeneration = 1
    val pipesKeptPerGeneration =
      pipesPerGeneration - (newPipesPerGeneration + newMutantsPerGeneration)

    def evalFunc: Double => Double = x => x
    val ticksPerEval = 1000 // How many ticks should each run last

    val inputChannels = List(0,1,2)
    val outputChannels = List(3,4,5)
  }

  object game {
    val width = 10000.0
    val height = 10000.0
    val speed = 10.0
    val turnRate = 0.01
    val viewPoints = 4
    val maxTurnRate = 0.01

    val sightRange = 3000.0
    val deadZone = 200.0
  }


  object gameVisualizer {
    val sixtyFPSrefreshRate = 17
    val thirtyFPSrefreshRate = 33

    /**
      Spike cooldown has a fairly important role for the visualizers since
      it essentially decides how often a new agent is calculated as the agent
      currently only moves on spike data
      */
    val agentMsgPerSec = experiment.maxSpikesPerSec
  }

  object waveformVisualizer {

    val vizHeight = 100
    val vizLength = 200
    val pointsPerSec = experiment.samplerate
    val blockSize = pointsPerSec/vizLength
    val reducedSegmentLength = experiment.segmentLength/blockSize
    val maxVal = 600
    val wfMsgSize = 1200
    val sixtyFPSrefreshRate = 17
    val thirtyFPSrefreshRate = 33
    val dataPointsReceivedPerSec = 60*(pointsPerSec/blockSize)
    val wfMsgSentPerSecond = dataPointsReceivedPerSec/wfMsgSize

    def printMe(): Unit = {
      println("----[Wafeform stream parameters]----")
      println(s"vizualizer height in pixels: $vizHeight")
      println(s"vizualizer length in pixels: $vizLength")
      println(s"\nIn order to fill one seconds worth of data in $vizLength pixels with a samplerate of $pointsPerSec")
      println(s"One out $blockSize datapoints are needed\n")
      println("By reducing the samplerate the segment length is reduced accordingly")
      println(s"The reduced segment length is $reducedSegmentLength")
      println(s"The web frontend receives ${dataPointsReceivedPerSec} datapoints per sec in total")
      println(s"in the form of ${wfMsgSentPerSecond} messages per sec")
    }
  }
}
