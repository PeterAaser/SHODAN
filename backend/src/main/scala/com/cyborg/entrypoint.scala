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

  // Effect for inner should be to write to a log, it should not need anything other than IO sockets.
  def inner[F[_]: Async](
    params: NeuroDataParams,
    meameReadStream: Stream[F, Byte],
    meameWriteSink: Stream[F, Byte] => F[Unit] ): F[Unit] =
  {

    val FF = Filters.FeedForward(
      List(2, 3, 2)
        , List(0.1, 0.2, 0.4, 0.0, 0.3)
        , List(0.1, -0.2, 0.2, -0.1, 2.0, -0.4, 0.1, -0.2, 0.1, 1.0, 0.3, 0.3))

    val inputFilter = Assemblers.assembleInputFilter
    val agentPipe = Assemblers.assembleAgentPipe(FF)

    val toStimFrequencyTransform: List[Double] => String = {
      val logScaler = logScaleBuilder(scala.math.E)
      toStimFrequency(List(3, 4, 5, 6), logScaler)
    }

    val meme = meameReadStream
      .through(utilz.bytesToInts)
      .through(inputFilter)
      .through(agentPipe)
      .through(_.map((λ: Agent) => {AgentService.agentUpdate(λ); λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      .through(text.utf8Encode)

    val memeTask = meameWriteSink(meme)

    println("INNER IS READY TO RUN")

    // meme.run
    memeTask
  }


  def outer[F[_]: Async]: F[Unit] = {

    // outerStore
    // outerRunStored
    outerRunSplitter
  }

  def outerStore[F[_]: Async]: F[Unit] = {
    val conf = ConfigFactory.load()
    val experimentConf = conf.getConfig("experimentConf")
    val params = NeuroDataParams(experimentConf)

    val meme = neuroServer.testThing(params)
    meme
  }

  def outerRunStored[F[_]: Async]: F[Unit] = {

    val filename = FW.getNewestFilename

    val meme = FW.meameDataReader(filename) flatMap {
      case (paramStream, dataStream) => {
        paramStream flatMap { params =>
          val meme = inner[F](params, dataStream, FW.meameLogWriter)
          println(params)

          Stream.eval(meme)
        }
      }
    }

    meme.run
  }

  def outerRunSplitter[F[_]: Async]: F[Unit] = {

    val filename = FW.getNewestFilename
    FW.channelSplitter(filename)

  }
}
