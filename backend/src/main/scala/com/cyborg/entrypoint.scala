package com.cyborg

import com.cyborg.params.NeuroDataParams
import com.cyborg.rpc.AgentService
import com.cyborg.wallAvoid.Agent
import com.typesafe.config.ConfigFactory
import fs2._
import fs2.util.Async
import MEAMEutilz._


object mainLoop {

  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")

  type agentFitnessFunction = Agent => Double

  // As of now inner does too much, including IO
  // Type information is missing here. What should the output type be? Should just rewrite the whole thing in idris YOLO
  def inner[F[_]: Async](
    params: NeuroDataParams,
    meameReadStream: Stream[F, Int],
    meameWriteSink: Stream[F, Byte] => F[Unit] ): F[Unit] =
  {

    val initFF = Filters.FeedForward(
      List(2, 3, 2)
        , List(0.1, 0.2, 0.4, 0.0, 0.3)
        , List(0.1, -0.2, 0.2, -0.1, 2.0, -0.4, 0.1, -0.2, 0.1, 1.0, 0.3, 0.3))

    val inputFilter = Assemblers.assembleInputFilter
    val agentPipe = Assemblers.assembleAgentPipe(initFF)

    val toStimFrequencyTransform: List[Double] => String = {
      val logScaler = logScaleBuilder(scala.math.E)
      toStimFrequency(List(3, 4, 5, 6), logScaler)
    }

    val meme = meameReadStream
      .through(inputFilter)
      .through(agentPipe)
      .through(_.map((λ: Agent) => {AgentService.agentUpdate(λ); λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      .through(text.utf8Encode)

    val memeTask = meameWriteSink(meme)

    memeTask
  }


  def outer[F[_]: Async]: F[Unit] = {
    // outerStore
    // outerRunStored
    // outerRunSplitter
    ???
  }

  def outerT: Task[Unit] = {
    // outerRunDBSplitter
    // outerRunFromDB
    // networkStoreToDB(doobieTasks.setupExperimentStorage)
    dumpToStoreToDB(doobieTasks.setupExperimentStorage)
  }

  def outerStore[F[_]: Async]: F[Unit] = {

    // val conf = ConfigFactory.load()
    // val experimentConf = conf.getConfig("experimentConf")
    // val params = NeuroDataParams(experimentConf)

    // val meme = neuroServer.testThing(params)

    // meme
    ???
  }


  def dumpToStoreToDB[F[_]: Async](sinks: F[List[Sink[F,Int]]]): F[Unit] = {

    // val maxQueueDepth = 256*256*8
    // val datapointsPerSweep = 1024
    // val channels = 60


    // val numericalDataStream: Stream[F,Int] =
    //   FW.meameDumpReader

    // val channelStreams = utilz.alternate(
    //   numericalDataStream,
    //   datapointsPerSweep,
    //   maxQueueDepth,
    //   channels
    // )

    // def writeChannelData(sink: Sink[F,Int], dataStream: Stream[F,Vector[Int]]): Stream[F,Unit] =
    //   dataStream
    //     .through(utilz.chunkify)
    //     .through(sink)

    // def writeTaskStream = channelStreams flatMap {
    //   channels: List[Stream[F,Vector[Int]]] => {
    //     Stream.eval(sinks) flatMap {
    //       sinks: List[Sink[F,Int]] => {
    //         val a =
    //           ((channels zip sinks)
    //              .map( { case (channel, sink) => (writeChannelData(sink, channel)) } ))

    //         Stream.emits(a)
    //       }
    //     }
    //   }
    // }
    // concurrent.join(200)(writeTaskStream).drain.run
    ???
  }

  // Sets up the machinery to store a proper recording from a live culture
  // into the database
  def networkStoreToDB[F[_]: Async](sinks: F[List[Sink[F,Int]]]): F[Unit] = {

    // val socketBufferSize = 1024*1024
    // val maxQueueDepth = 256*256
    // val datapointsPerSweep = 1024
    // val channels = 60

    // val dataStream: Stream[F, Byte] = neuroServer.socketStream flatMap (
    //   meameSocket => (meameSocket.reads(socketBufferSize)))

    // val numericalDataStream =
    //   dataStream.through(utilz.bytesToInts)

    // val channelStreams = utilz.alternate(
    //   numericalDataStream,
    //   datapointsPerSweep,
    //   maxQueueDepth,
    //   channels
    // )

    // def writeChannelData(sink: Sink[F,Int], dataStream: Stream[F,Vector[Int]]): Stream[F,Unit] =
    //   dataStream
    //     .through(utilz.chunkify)
    //     .through(sink)

    // def writeTaskStream = channelStreams flatMap {
    //   channels: List[Stream[F,Vector[Int]]] => {
    //     Stream.eval(sinks) flatMap {
    //       sinks: List[Sink[F,Int]] => {
    //         val a =
    //           ((channels zip sinks)
    //              .map( { case (channel, sink) => (writeChannelData(sink, channel)) } ))

    //         Stream.emits(a)
    //       }
    //     }
    //   }
    // }
    // concurrent.join(200)(writeTaskStream).drain.run
    ???
  }

  def outerRunFromDB: Task[Unit] = {

    // val filename = FW.getNewestFilename
    // val params = NeuroDataParams(40000, List(2,5,8,16), List(4,6,17,24), 512)

    // val meme: Task[Unit] =
    //   inner[Task](params, DatabaseIO.databaseStream(doobieTasks.filteredChannelStreams), FW.meameLogWriter)

    // meme
    ???
  }

  def outerRunStored[F[_]: Async]: F[Unit] = {

    // val filename = FW.getNewestFilename
    // val params = NeuroDataParams(40000, List(2,5,8,16), List(4,6,17,24), 512)

    // val meme: F[Unit] =
    //   inner[F](params, FW.meameDataReader(filename), FW.meameLogWriter)

    // meme
    ???
  }

  def outerRunDBSplitter: Task[Unit] = {
    // val filename = FW.getNewestFilename
    // val sinks = doobieTasks.setupExperimentStorage
    // FW.genericChannelSplitter(filename, sinks)
    ???
  }
}
