package cyborg

import cats._
import cats.data._
import cats.implicits._

import cats.effect._
import cats.effect.concurrent._

import fs2._
import fs2.concurrent.Topic
import fs2.concurrent.SignallingRef
import fs2.concurrent.Queue

import cyborg.dsp.calls.DspCalls._
import cyborg.DspRegisters._
import cyborg.MEAMEmessages._
import cyborg.utilz._

import scala.concurrent.duration._



/**
  * An RNN that runs asynchronously, approximating how neural tissue
  * interfaces with SHODAN.
  * 
  */
class RecurrentReservoir[F[_] : Concurrent : Timer] {

  // Shitty stim req pipe. It's either on or off
  def stimuliRequestPipe: Pipe[F,(Int,Option[FiniteDuration]), DspFuncCall] = {

    def setPeriod(idx: Int) = DspFuncCall(
      SET_ELECTRODE_GROUP_PERIOD,
      List((STIM_QUEUE_GROUP, idx), (STIM_QUEUE_PERIOD, 10)))

    def enable(idx: Int) = DspFuncCall(
      ENABLE_STIM_GROUP,
      List((STIM_QUEUE_GROUP, idx)))

    def disable(idx: Int) = DspFuncCall(
      DISABLE_STIM_GROUP,
      List((STIM_QUEUE_GROUP, idx)))

    def go(s: Stream[F, (Int,Option[FiniteDuration])]): Pull[F, DspFuncCall, Unit] = {
      s.pull.uncons1.flatMap {
        case Some(((idx, Some(period)), tl)) => {
          Pull.output1(setPeriod(idx)) >>
          Pull.output1(enable(idx)) >>
          go(tl)
        }

        case Some(((idx, None), tl)) => {
          Pull.output1(disable(idx)) >>
          go(tl)
        }

        case Some((_, tl)) => go(tl)
        case None => Pull.done
      }
    }

    ins => go(ins).stream
  }


  def start(messages: Ref[F, List[DspFuncCall]], topic: Topic[F,Chunk[Double]]): F[Unit] = {
    val myRNN = new RNN(500, 5, -0.2, 1.0, -0.5, 10, 2.0, 0.2)
    val tickSource = Stream.fixedRate(2.millis)

    def step: F[Chunk[Double]] = for {
      msg <- messages.get
      _   <- messages.set(Nil)
    } yield {
      msg.foreach(decodeDspCall(_))
      myRNN.stepN(1)(0)
    }

    def decodeDspCall(call: DspFuncCall): Unit = {
      def idx = call.args.toMap.apply(STIM_QUEUE_GROUP)
      def nextPeriod = call.args.toMap.apply(STIM_QUEUE_PERIOD)

      call.func match {
        case  SET_ELECTRODE_GROUP_PERIOD => myRNN.updateStimGroup(idx, nextPeriod)
        case  ENABLE_STIM_GROUP          => myRNN.enableStimGroup(idx)
        case  DISABLE_STIM_GROUP         => myRNN.disableStimGroup(idx)
        case _                           => ()
      }
    }


    tickSource.evalMap{ _ => step.flatMap(topic.publish1) }.compile.drain
  }
}
