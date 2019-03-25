package cyborg

import fs2._
import fs2.concurrent._

import cats.effect._
import cats._
import cats.implicits._
import backendImplicits._

import utilz._


/**
  * A very simple reservoir, exhibiting relatively controlled
  * non-linear behavior, making tasks such as xor very simple
  * 
  * Changes only on update, but exposes an asynchronous interface
  */
class SimpleReservoir() {

  val reservoirState = (0 until 9).map(_ => 0.0).toArray

  private def perturb(perturbation: Chunk[Double]): Unit = {
    assert(perturbation.size == 2, "This reservoir expects 2 intputs fam")
    val p1 = perturbation(0)/params.game.sightRange
    val p2 = perturbation(1)/params.game.sightRange

    reservoirState(0) = p1 + p2
    reservoirState(1) = p1 - p2
    reservoirState(2) = p2 - p1
    reservoirState(3) = Math.max(p1, p2)
    reservoirState(4) = Math.min(p1, p2)
    reservoirState(5) = if(p1 > p2) -1.0 else 1.0
    reservoirState(6) = p1 * p2
    reservoirState(7) = if(p1 != 0.0) p2/p1 else 0.0
    reservoirState(8) = if(p2 != 0.0) p1/p2 else 0.0
  }

  val perturbationSink: Pipe[IO,Chunk[Double],Unit] = _.evalMap(x => IO { perturb(x) })
}


/**
  * Simplest possible linear reservoir
  */
class EchoReservoir() {

  val reservoirState = (0 until 2).map(_ => 0.0).toArray

  private def perturb(perturbation: Chunk[Double]): Unit = {
    assert(perturbation.size == 2, "This reservoir expects 2 intputs fam")
    val p1 = perturbation(0)/params.game.sightRange
    val p2 = perturbation(1)/params.game.sightRange

    reservoirState(0) = p1
    reservoirState(1) = p2
  }

  val perturbationSink: Pipe[IO,Chunk[Double],Unit] = _.evalMap(x => IO { perturb(x) })
}
