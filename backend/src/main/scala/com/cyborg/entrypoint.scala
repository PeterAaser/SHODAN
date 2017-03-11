package com.cyborg

import com.cyborg.rpc.AgentService
import com.cyborg.wallAvoid.Agent
import fs2.Task
import fs2._
import fs2.async.mutable.Signal
import fs2.util.Async
import MEAMEutilz._
import java.nio.channels.AsynchronousChannelGroup


object mainLoop {

  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(8, threadName = "fugger")

  type agentFitnessFunction = Agent => Double

  // Effect for inner should be to write to a log, it should not need anything other than IO sockets.
  def inner[F[_]: Async](
    meameReadStream: Stream[F, Byte],
    meameWriteSink: Sink[F, Byte] ): F[Unit] =
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
      .through(meameWriteSink)

    meme.run
  }

  def outer[F[_]: Async]: F[Unit] = {
    val meme = neuroServer.testThing
    meme
  }

  // def _outer(a: Int): Task[Unit] = {

  //   val timer: Task[Signal[Task, Int]] = async.signalOf[Task, Int](1)
  //   val meme: Task[Int] = timer.flatMap {
  //     x1 => x1.get
  //   }

  //   val otherMeme: Stream[Task, Signal[Task, Int]] = Stream.eval(timer)

  //   val fugg: Stream[Task, Int] = Stream.eval(timer) flatMap { x => x.discrete }

  //   val ohno: Task[Unit] = timer flatMap { x1 => x1.set(2) }

  //   val evaluatedSignal: Signal[Task, Int] = timer.unsafeRun

  //   ???
  // }
}
