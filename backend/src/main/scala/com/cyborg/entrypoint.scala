package com.cyborg

import com.cyborg.params.NeuroDataParams
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
    frontendAgentObservePipe: Pipe[F,Agent,Agent],
    channels: List[Int]): F[Unit] =
  {

    GArun(meameReadStream, meameWriteSink, frontendAgentObservePipe, channels)
  }


  def outerT: Task[Unit] = {
    ???
  }


  def GArun[F[_]:Async](
    meameReadStream: Stream[F, Int],
    meameWriteSink: Sink[F, Byte],
    frontendAgentObservePipe: Pipe[F,Agent,Agent],
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
      .through(frontendAgentObservePipe)
      .through(_.map((λ: Agent) => {λ.distances}))
      .through(_.map(toStimFrequencyTransform))
      .through(_.map( λ => { println(λ); λ } ) )
      .through(text.utf8Encode)

    val memeTask = meme.through(meameWriteSink)

    memeTask.run
  }
}
