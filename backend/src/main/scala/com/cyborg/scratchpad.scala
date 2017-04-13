package SHODAN

import scala.language.higherKinds

import fs2._
import fs2.util.Async
import fs2.async.mutable.Queue
import fs2.io.file._
import java.nio.file._
import java.net.InetSocketAddress

object what {

  import scala.concurrent.duration._

  implicit val strategy: Strategy = Strategy.fromFixedDaemonPool(8)
  implicit val scheduler: Scheduler = Scheduler.fromFixedDaemonPool(8)

  def sLog[A](prefix: String): Pipe[Task,A,A] = {
    _.evalMap { a => Task.delay{ println(s"$prefix> $a"); a }}
  }

  def randomDelays[A](max: FiniteDuration): Pipe[Task,A,A] = _.evalMap { a =>
    val delay = Task.delay(scala.util.Random.nextInt(max.toMillis.toInt))
    delay.flatMap { d => Task.now(a).schedule(d.millis) }
  }

  def doThing(): Unit = {
    val Astream = Stream.range(1, 10).through(randomDelays(1.second)).through(sLog("A"))
    val Bstream = Stream.range(1, 10).through(randomDelays(1.second)).through(sLog("B"))
    val Cstream = Stream.range(1, 10).through(randomDelays(1.second)).through(sLog("C"))

    // (Astream interleave Bstream).through(sLog("interleaved")).run.unsafeRun
    // (Astream merge Bstream).through(sLog("merged")).run.unsafeRun
    // (Astream either Bstream).through(sLog("either")).run.unsafeRun

    ((Astream merge (Bstream merge Cstream)))

    // This badboy is a stream of streams
    // A tcp server will look like this, emitting streams for incoming connections (or sockets)
    val memeStream: Stream[Task, Stream[Task, Int]] = Stream(Astream, Bstream, Cstream)

    // To flatten we can use concurrent join, allowing us to work with (in this case)
    // 3 streams in parallel
    val flattened: Stream[Task, Int] = concurrent.join(3)(memeStream).through(sLog("merged"))
    flattened.run.unsafeRun

    val manyStreams: Stream[Task, Stream[Task, Int]] = Stream.range(0, 10).map { id =>
      Stream.range(1, 10).through(randomDelays(1.second)).through(sLog(('A' + id).toChar.toString))
    }

    // concurrent.join(3)(manyStreams).run.unsafeRun

    val someSignal = async.signalOf[Task,Int](1)
    val someSignalGet: Task[Int] = someSignal.flatMap { λ => λ.get }
    println(someSignalGet.unsafeRun)
    println(Stream.eval(someSignalGet).runLog.unsafeRun)

  }

  def doAsyncThing(): Unit = {
    // val x = async.signalOf[Task, Int](1)
    // val s = x.unsafeRun
    // val y = s.discrete.through(sLog("some signal")).run.unsafeRunAsyncFuture
    // s.set(2).unsafeRun
    // val nig = for(i <- 0 until 100000){i*i}
    // s.modify(_ + 1).unsafeRun
    // val nig2 = for(i <- 0 until 100000){i*i}
    // s.modify(_ + 1).unsafeRun

    // println(s.continuous.take(100).runLog.unsafeRun)

    val meme = Stream.eval(async.signalOf[Task,Int](0)).flatMap { s =>

      val monitor: Stream[Task,Nothing] =
        s.discrete.through(sLog("s updated")).drain
      val data: Stream[Task,Int] =
        Stream.range(10, 20).through(randomDelays(1.second))
      val writer: Stream[Task,Unit] =
        data.evalMap { d => s.set(d) }

      monitor mergeHaltBoth writer

    }

    val qeme: Stream[Task,Unit] = Stream.eval(async.boundedQueue[Task,Int](5)).flatMap { q =>

      val monitor: Stream[Task,Nothing] =
        q.dequeue.through(sLog("deq'd")).drain

      val data: Stream[Task,Int] =
        Stream.range(10, 20).through(randomDelays(1.second))

      val writer: Stream[Task,Unit] = data.to(q.enqueue)

      monitor mergeHaltBoth writer

    }



    // meme.run.unsafeRun
    // qeme.run.unsafeRun
  }

  def alternate[F[_]: Async, A]
    ( src: Stream[F, A]
    , count: Int
    , maxQueued: Int
    ):
      Stream[F, (Stream[F, A], Stream[F, A])] = {

    def loop
      ( activeQueue: Queue[F, A]
      , alternateQueue: Queue[F, A]
      , remaining: Int
      ): Handle[F, A] => Pull[F, A, Unit] = h => {

      if (remaining <= 0) loop(alternateQueue, activeQueue, count)(h)
      else h.receive1 { (a, h) => Pull.eval(activeQueue.enqueue1(a)) >>
                         loop(activeQueue, alternateQueue, remaining - 1)(h) }
    }

    val mkQueue: F[Queue[F, A]] = async.boundedQueue[F, A](maxQueued)
    Stream.eval(mkQueue).flatMap { q1 =>
      Stream.eval(mkQueue).flatMap { q2 =>
        src.pull(loop(q1, q2, count)).drain merge Stream.emit(( q1.dequeue, q2.dequeue ))
      }
    }
  }

  def testThing = {
    val someInts: Stream[Task,Int] = ( Stream(1, 1, 1, 1, 1) ++ Stream(3, 3, 3, 3, 3) ).repeat
    val alternating1 = alternate(someInts, 5, 5).flatMap{ x => x._1 }
    val alternating2 = alternate(someInts, 5, 5).flatMap{ x => x._2 }
    (alternating1.map(λ => print(s"[$λ]")).run.unsafeRunAsyncFuture)
    println()
    (alternating2.map(µ => print(s"($µ)")).run.unsafeRunAsyncFuture)

  }

}

object memes {

  import scala.concurrent.duration._

  import fs2._
  import fs2.io.file._
  import java.nio.file._
  import simulacrum._

  def myTake[F[_],O](n: Int)(h: Handle[F,O]): Pull[F,O,Nothing] = {
    for {
      (chunk, h) <- if (n <= 0) Pull.done else h.awaitLimit(n)
      tl <- Pull.output(chunk) >> myTake(n - chunk.size)(h)
    } yield tl
  }

  implicit val strategy: Strategy = Strategy.fromFixedDaemonPool(8)

  def myTake_[F[_],O](n: Int)(h: Handle[F,O]): Pull[F,O,Nothing] = {
    if (n <= 0) Pull.done else h.awaitLimit(n)
      .flatMap {case (chunk, h) => Pull.output(chunk) >> myTake_(n - chunk.size)(h)}
  }

  def takePipe[F[_],I](n: Int): Pipe[F,I,I] = {
    def go(n: Int): Handle[F,I] => Pull[F,I,Unit] = h => {
      if (n <= 0) Pull.done
      else h.receive1 { case (a, h) =>
        Pull.output1(a) >> go(n - 1)(h)
      }
    }
    in => in.pull(go(n))
  }

  val someInts = Stream(0, 1, 2, 0, 0, 5, 10, 50, 10, 5, 0, 0, 0, 0, 0)
  val someDubs = someInts.map(_.toDouble)

  def mean[T : Numeric](xs: Iterable[T]): T = implicitly[Numeric[T]] match {
    case num: Fractional[_] => import num._; xs.sum / fromInt(xs.size)
    case num: Integral[_] => import num._; xs.sum / fromInt(xs.size)
    case _ => sys.error("Undivisable numeric!")
  }

  def meanPipe[F[_],I](windowSize: Int)(implicit ev: Numeric[I]): Pipe[F,I,I] = {

    import ev._

    def goMean(window: List[I]): Handle[F,I] => Pull[F,I,Unit] = h => {
      h.receive1 { case (a, h) =>
        Pull.output1(mean(window)) >>
          goMean(a :: window.init)(h)
      }
    }

    def goFill(window: List[I]): Handle[F,I] => Pull[F,I,Unit] = h => {
      if (window.length >= windowSize) goMean(window)(h)
      else
        h.receive1 { case (a, h) =>
          goFill(a :: window)(h)
        }
    }

    in => in.pull(goFill(List[I]()))
  }

  def getMeansTest(): Unit = {
    val nums = someInts.pure.through(meanPipe(5)).take(10).toList
    val nums2 = someDubs.pure.through(meanPipe(5)).take(10).toList
    println(nums)
    println(nums2)

  }

  def takePipeC[F[_],I](n: Int): Pipe[F,I,I] = {

    def go(n: Int): Handle[F,I] => Pull[F,I,Unit] = h => {
      if (n <= 0) Pull.done
      else h.receive { case (chunk, h) =>
        if(chunk.size > n) Pull.output(chunk.take(n))
        else Pull.output(chunk) >> go(n - chunk.size)(h)
      }
    }
    in => in.pull(go(n))
  }
}


object testingStuff {

  val someSource: Stream[Task, Double] = ???
  val someSink: Sink[Task, Double] = ???

  type Factor = Double
  type Score = Double
  val initFactor: Factor = 1.0

  def mutateFactor(factor: Factor): Factor = ???

  def createTestPipe(factor: Factor): Pipe[Task, Double, Double] =
    stream => stream.through(_.map(_*factor))

  def createTestPipes(seed: Factor) =
    List.fill(3)(mutateFactor(seed)).map(factor => (factor, createTestPipe(factor)))

  def searchingPipe(ticks: Int): Pipe[Task, Double, Double] = {

    // def init: Handle[Task, Double] => Pull[Task, Double, Unit] = {
    //   val pipes: List[Pipe[Task, Double, Double]] = createTestPipes(initTransform)
    //   ???
    // }


    // Takes n factor score tuples and creates the next n pipes
    type evaluatorCreaterPipe[F[_]] = Pipe[F,(Factor, Score), Stream[F,Pipe[F,Double,Double]]]

    type topLevelPipe[F[_]] = Pipe[F,Double,Double]

    type evaluatorStream[F[_]] = Stream[F,Pipe[F,Double,Double]]

    // Ideally this pipe should be used for n values or some other predicate and emit a score
    type evaluatorPipe[F[_]] = Pipe[F, Pipe[F,Double,Double], Score]

    def runPipes(
      ticks: Int,
      tested: List[(Factor, Score)],
      unTested: List[(Factor, Pipe[Task, Double, Double])]): Pipe[Task, Double, Double] = {

      s: Stream[Task, Double] => {
        if(ticks > 0){
          val currentPipe: Pipe[Task, Double, Double] = unTested.head._2
          val currentScore: Score = unTested.head._1
          def thing: Handle[Task, Double] => Pull[Task, Double, Unit] = h => {
            h.receive1 {
              case (score, h) => {
                Pull.output1(score)
              }
            }
            ???
          }
        }
      }

      ???
    }
    ???
  }
}
