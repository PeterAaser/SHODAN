package com.cyborg

import com.cyborg.params.NeuroDataParams
import com.cyborg.rpc.AgentService
import com.cyborg.wallAvoid.Agent
import com.typesafe.config.ConfigFactory
import fs2._
import fs2.util.Async
import MEAMEutilz._


object mainLoop {

  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(16, threadName = "fugger")

  type agentFitnessFunction = Agent => Double

  // Sort of nearing irrelevancy
  def inner[F[_]: Async](
    meameReadStream: Stream[F, Int],
    meameWriteSink: Sink[F, Byte],
    channels: List[Int]): F[Unit] =
  {

    GArun(meameReadStream, meameWriteSink, channels)
    // normalRun(meameReadStream, meameWriteSink)
  }


  def outerT: Task[Unit] = {
    IO.ghettoRunFromTCP(1000, List(3, 10, 15, 20))
  }


  def GArun[F[_]:Async](
    meameReadStream: Stream[F, Int],
    meameWriteSink: Sink[F, Byte],
    channels: List[Int]): F[Unit] =
  {
    // hardcoded
    val MAGIC_PERIOD = 1000
    val MAGIC_SAMPLERATE = 1000
    val MAGIC_THRESHOLD = 1000
    val pointsPerSweep = 1000

    // hardcoded
    val layout = List(2,3,2)

    val inputFilter = Assemblers.assembleInputFilter(channels)
    val spikes = meameReadStream.through(inputFilter)

    val experimentPipe = GApipes.experimentPipe(spikes, layout)

    val toStimFrequencyTransform: List[Double] => String = {
      val logScaler = logScaleBuilder(scala.math.E)
      toStimFrequency(List(3, 6, 9), logScaler)
    }

    val meme = experimentPipe
      .through(_.map((λ: Agent) => {AgentService.agentUpdate(λ); λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      .through(text.utf8Encode)

    val memeTask = meme.through(meameWriteSink)

    memeTask.run
  }


  def GArun2[F[_]:Async](
    meameReadStream: Stream[F, Int],
    meameWriteSink: Sink[F, Byte]): F[Unit] =
  {
    // hardcoded
    val MAGIC_PERIOD = 1000
    val MAGIC_SAMPLERATE = 1000
    val MAGIC_THRESHOLD = 1000
    val pointsPerSweep = 1000

    // hardcoded
    val channels = List(4, 13, 17, 24)

    // hardcoded
    val layout = List(2,3,2)

    val inputFilter = Assemblers.assembleInputFilter(channels)
    val spikes = meameReadStream.through(inputFilter)

    val experimentPipe = GApipes.experimentPipe(spikes, layout)

    val toStimFrequencyTransform: List[Double] => String = {
      val logScaler = logScaleBuilder(scala.math.E)
      toStimFrequency(List(3, 4, 5, 6), logScaler)
    }

    val meme = experimentPipe
      .through(_.map((λ: Agent) => {AgentService.agentUpdate(λ); λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      .through(text.utf8Encode)

    val memeTask = meameWriteSink(meme)

    memeTask.run
  }


  def normalRun[F[_]:Async](
    meameReadStream: Stream[F, Int],
    meameWriteSink: Sink[F, Byte],
    channels: List[Int]): F[Unit] =
  {
    val initFF = Filters.FeedForward(
      List(2, 3, 2)
        , List(0.1, 0.2, 0.4, 0.0, 0.3)
        , List(0.1, -0.2, 0.2, -0.1, 2.0, -0.4, 0.1, -0.2, 0.1, 1.0, 0.3, 0.3))

    val inputFilter = Assemblers.assembleInputFilter(channels)
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
    memeTask.run
  }
}
