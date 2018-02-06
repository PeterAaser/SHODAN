package cyborg


object params {

  def printParams(): Unit = {
    experiment.printMe()
    filtering.printMe()
    waveformVisualizer.printMe()
  }

  object experiment {
    val totalChannels = 60
    val samplerate = 20000
    val segmentLength = 2000
    val maxSpikesPerSec = 50

    val minFreq = 0.33
    val maxFreq = 10.0

    val DSPticksPerSecond = 50000

    def printMe(): Unit = {
      println(Console.YELLOW + "----[Experiment params]----")
      println(Console.YELLOW + s"samplerate: \t\t" + Console.RED + s"$samplerate")
      println(Console.YELLOW + s"segment length: \t\t" + Console.RED + s"$segmentLength")
      println(Console.YELLOW + s"max spikes per second: \t" + Console.RED + s"$maxSpikesPerSec")
      println(Console.YELLOW + "refactory period for spike detector pipe: " + Console.RED + s"${samplerate/maxSpikesPerSec}")
      println("\n\n")
      println(Console.RESET)
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
    val sawtooth = 12341
    val sendBufSize = 4096        // rather low bandwidth required for stimreqs
    val recvBufSize = 262144      // 262144 = 1024*256, 256kb, matches kernel tcp socket buffer size
  }


  object filtering {

    val MAGIC_THRESHOLD = 1000
    val layout = List(2,3,2) // Layout of the neural network
    val movingAverageRetention = 10 // should depend on samplerate

    val spikeCooldown = experiment.samplerate/experiment.maxSpikesPerSec


    def printNetworkLayout(): Unit = {
      layout.foreach { λ => print(Console.CYAN + s"[$λ]") }
      println()
    }

    def printMe(): Unit = {
      println(Console.CYAN + "----[Filtering params]----")
      println(Console.CYAN + s"Spike thresholding value is set to " + Console.RED + s"$MAGIC_THRESHOLD")
      println(Console.CYAN + s"The current neural network layout is: ")
      printNetworkLayout()
      println(Console.CYAN + s"The input of to the neural network is the moving average of the last " + Console.RED + s"$movingAverageRetention spikes")
      println(Console.CYAN + s"the 'cooldown' in samples between each possible spike is" + Console.RED + s" $spikeCooldown")
      println(Console.CYAN + "\n\n")
      println(Console.RESET)
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
    // val outputChannels = List(3,4,5,6)

    val outputChannels = List(List(0,1), List(12,13), List(22), List(29))
    val outputChannelsBits: List[List[Int]] = {
      val lower = outputChannels.map(_.filter(_ < 30)).map(_.foldLeft(0)((acc,µ) => acc + (1 << µ)))
      val upper = outputChannels.map(_.filter(_ >= 30)).map(_.foldLeft(0)((acc,µ) => acc + (1 << (µ % 30))))
      (lower zip upper).map(λ => List(λ._1, λ._2))
    }

  }

  object game {
    val width = 10000.0
    val height = 10000.0
    val speed = 1.0
    val turnRate = 0.001
    val viewPoints = 4
    val maxTurnRate = 0.001

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

    val maxVal = 1300
    val wfMsgSize = 1200
    val sixtyFPSrefreshRate = 17  //every 17 ms
    val thirtyFPSrefreshRate = 33 //every 33 ms
    val dataPointsReceivedPerSec = 60*(pointsPerSec/blockSize)

    val wfMsgSentPerSecond = dataPointsReceivedPerSec/wfMsgSize

    def printMe(): Unit = {
      println(Console.MAGENTA)
      println(Console.MAGENTA + "----[Wafeform stream parameters]----")
      println(Console.MAGENTA + s"vizualizer height in pixels: " + Console.RED + s"$vizHeight")
      println(Console.MAGENTA + s"vizualizer length in pixels: " + Console.RED + s"$vizLength\n")
      println(Console.MAGENTA + s"In order to fill one seconds worth of data in " + Console.RED + s"$vizLength " + Console.MAGENTA + "pixels with a samplerate of " + Console.RED + s"$pointsPerSec")
      println(Console.MAGENTA + s"One out " + Console.RED + s"$blockSize " + Console.MAGENTA + "datapoints are needed\n")
      println(Console.MAGENTA + s"By reducing the samplerate the segment length is reduced accordingly")
      println(Console.MAGENTA + s"The reduced segment length is " + Console.RED + s"$reducedSegmentLength")
      println(Console.MAGENTA + s"The web frontend receives " + Console.RED + s"${dataPointsReceivedPerSec} " + Console.MAGENTA + "datapoints per sec in total")
      println(Console.MAGENTA + s"in the form of " + Console.RED + s"${wfMsgSentPerSecond} " + Console.MAGENTA + "messages per sec")
      println("\n\n")
      println(Console.RESET)
    }
  }

  object StorageParams {

    val storageType = "CSV"
    val toplevelPath = "/home/peteraa/MEAdata/"

  }
}
