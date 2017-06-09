package com.cyborg


object params {

  object experiment {
    val samplerate = 1000
    val segmentLength = 100
    val maxSpikesPerSec = 50

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

  object filtering {

    val MAGIC_THRESHOLD = 1000
    val layout = List(2,3,2) // Layout of the neural network
    val movingAverageRetention = 10

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
    }

  }

  object GA {
    val pipesPerGeneration = 6
    val newPipesPerGeneration = 2
    val newMutantsPerGeneration = 1
    val pipesKeptPerGeneration =
      pipesPerGeneration - (newPipesPerGeneration + newMutantsPerGeneration)

    def evalFunc: Double => Double = x => x
    val ticksPerEval = 200 // How many ticks should each run last
  }

  object game {
    val width = 10000.0
    val height = 10000.0
    val speed = 10.0
    val turnRate = 0.01
    val viewPoints = 4
    val maxTurnRate = 0.01
  }

  object waveformVisualizer {
    val vizHeight = 60
    val vizLength = 200
    val pointsPerSec = experiment.samplerate
    val scalingFactor = 2000

    val blockSize = pointsPerSec/vizLength
    // val blockSize = 2000
  }

}
