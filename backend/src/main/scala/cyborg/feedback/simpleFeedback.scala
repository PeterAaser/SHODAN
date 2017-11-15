package cyborg

import fs2._
import fs2.Stream._

import cats.effect.IO

import cats.effect.Effect
import fs2.async.mutable.Queue
import scala.concurrent.ExecutionContext

import utilz._

/**
  For this test

  ReservoirOutput :  Int
  FilterOutput    :  String
  O               :  IO[Unit]

  */

object simpleFeedback {

  type ReservoirOutput =  Int
  type FilterOutput    =  String
  type O               =  IO[Unit]

  type Filter          = Pipe[IO, ReservoirOutput, Option[FilterOutput]]

  val resOut: Stream[IO,ReservoirOutput] = Stream.iterate(0)(_+1)

  val size = 30

  // lol...
  // It's 19:55:50, don't blame me
  def myCreateSimrunner: () => Pipe[IO, FilterOutput, O] = () => { (in: Stream[IO, FilterOutput]) =>
    in.map(λ => IO(println(λ)))
  }


  def createFilter(c: Int): Pipe[IO,ReservoirOutput,Option[FilterOutput]] = {
    val outerCountString = s"Pipe number $c: "
    def go(s: Stream[IO,ReservoirOutput], ec: Int): Pull[IO, Option[FilterOutput], Unit] = {
      if (ec == size){
        Pull.output1(None)
      }
      else
        s.pull.uncons1 flatMap {
          case Some((a, tl)) => {
            val innerCountString = s"received element $ec with value $a"
            Pull.output1(Some(outerCountString ++ innerCountString)) >> go(tl, ec + 1)
          }
        }
    }
    in => go(in, 0).stream
  }


  def myEval: Pipe[IO, O, Double] =
    _.through(mapN(size, _ => 1.0)).through(_.map{λ => println("one eval done"); λ})


  def myGenerator: Pipe[IO, Double, Filter] = {

    def init(s: Stream[IO,Double]): Pull[IO, Filter, Unit] = {
      Pull.output1(createFilter(0)) >>
        go(s, 1)
    }

    def go(s: Stream[IO, Double], c: Int): Pull[IO, Filter, Unit] = {
      s.pull.uncons1 flatMap {
        case Some((_, tl)) => {
          val memer = createFilter(c)
          Pull.output1(memer) >> go(tl, c+1)
        }
        case None => {
          println("myGenerator died")
          Pull.done
        }
      }
    }
    in => init(in).stream
  }


  def expPipe(implicit ec: EC) =
    Feedback.experimentPipe[IO, ReservoirOutput, FilterOutput, O](myCreateSimrunner, myEval, myGenerator)

  def doIt(implicit ec: EC): Stream[IO,IO[Unit]] = resOut.take(400).through(expPipe)
}
