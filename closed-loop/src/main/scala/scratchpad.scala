package SHODAN

import scala.language.higherKinds

object dubi {

  import doobie.imports._
  import doobie.util.compat.cats.monad._ // todo: make this automatic
  import cats._, cats.data._, cats.implicits._

  def doDB = {
    val xa = DriverManagerTransactor[IOLite](
      "org.postgresql.Driver", "jdbc:postgresql:world", "postgres", ""
    )

    val program1 = 42.pure[ConnectionIO]

    val task = program1.transact(xa)
    // println(task.unsafePerformIO)
  }

}

object what {

  import fs2._
  import fs2.util.Async
  import fs2.async.mutable.Queue
  import fs2.io.file._
  import java.nio.file._
  import java.net.InetSocketAddress

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

  def doThing = {
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

  def doAsyncThing: Unit = {
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
    // for {
    //   q1 <- Stream.eval(mkQueue)
    //   q2 <- Stream.eval(mkQueue)
    //   out <- src.pull(loop(q1, q2, count)).drain merge Stream.emit(q1.dequeue -> q2.dequeue)
    // } yield out
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

  def getMeansTest = {
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


object typeClutter {

  /**
    Our asininie story begins with an attempt at making a fanfiction generator

    The constraints are that it is tasteless to have a fanfic where the love interests
    have different franchises
    */

  object Franchise {
    case class Character(name: String)
  }

  class Franchise(name: String) {
    import Franchise.Character
    def createFanFiction(
      lovestruck: Character,
      objectOfDesire: Character): (Character, Character) = (lovestruck, objectOfDesire)
  }

  val starTrek = new Franchise("Star Trek")
  val starWars = new Franchise("Star Wars")

  val quark = Franchise.Character("Quark")
  val jadzia = Franchise.Character("Jadzia Dax")

  val luke = Franchise.Character("Luke Skywalker")
  val yoda = Franchise.Character("Yoda")


  /**
    What we don't want is the following:
    */
  starWars.createFanFiction(lovestruck = yoda, objectOfDesire = jadzia)

  /**
    What about path dependant types?
    */

  class A {
    class B
    var b: Option[B] = None
  }
  val a1 = new A
  val a2 = new A
  val b1 = new a1.B
  val b2 = new a2.B
  a1.b = Some(b1)
  // a2.b = Some(b1) // does not compile

  object LetsTryThat {

    class Franchise(name: String) {
      case class Character(name: String)
      def createFanFictionWith(
        lovestruck: Character,
        objectOfDesire: Character): (Character, Character) = (lovestruck, objectOfDesire)
    }

    val starTrek = new Franchise("Star Trek")
    val starWars = new Franchise("Star Wars")

    val quark = starTrek.Character("Quark")
    val jadzia = starTrek.Character("Jadzia Dax")

    val luke = starWars.Character("Luke Skywalker")
    val yoda = starWars.Character("Yoda")

    starTrek.createFanFictionWith(quark, jadzia)

    // no compilados
    // starTrek.createFanFictionWith(luke, jadzia)

    // no compilados
    // starTrek.createFanFictionWith(luke, yoda)
  }

  /**
    Our next adventure details some real webscale shit:
    */

  object AwesomeDB {
    abstract class Key(name: String) {
      type Value
    }
  }
  import AwesomeDB.Key
  class AwesomeDB {
    import collection.mutable.Map
    val data = Map.empty[Key, Any]
    def get(key: Key): Option[key.Value] = data.get(key).asInstanceOf[Option[key.Value]]
    def set(key: Key)(value: key.Value): Unit = data.update(key, value)
  }

  trait IntValued extends Key {
    type Value = Int
  }

  trait StringValued extends Key {
    type Value = String
  }

  object Keys {
    val foo = new Key("foo") with IntValued
    val bar = new Key("bar") with StringValued
  }

  val dataStore = new AwesomeDB
  dataStore.set(Keys.foo)(23)
  val i: Option[Int] = dataStore.get(Keys.foo)

  // no compiladas
  // dataStore.set(Keys.foo)("23") // does not compile

  /**
    Next up our brave heroes have a go at existential types (haha me=>irl xD)
    */

  class Blah {
    type Member
  }

  class Blah2[Param]

  /**
    Score one for parametrization
    */

  val myBlah = new Blah {type Member = Int}
  val myBlah2 = new Blah2[Int]

  /**
    A more involved example on type members vs type params is the list
    */

  /**
    Fairly succinct
    */
  sealed abstract class ParamList[T]
  final case class ParamNil[T]() extends ParamList[T]
  final case class ParamCons[T](head: T, tail: ParamList[T]) extends ParamList[T]

  /**
    Cluttered with 'type refinements' ({type T = some type})
    */
  sealed abstract class MemberList {self =>
    type T
    def uncons: Option[MemberCons {type T = self.T}]
  }
  sealed abstract class MemberNil extends MemberList {
    def uncons = None
  }
  sealed abstract class MemberCons extends MemberList {self =>
    val head: T
    val tail: MemberList {type T = self.T}
    def uncons = Some(self: MemberCons {type T = self.T})
  }

  def MemberNil[T0](): MemberNil {type T = T0} =
    new MemberNil {
      type T = T0
    }

  def MemberCons[T0](hd: T0, tl: MemberList {type T = T0})
      : MemberCons {type T = T0} =
    new MemberCons {
      type T = T0
      val head = hd
      val tail = tl
    }

  /**
    It seems we can get by with skipping one of the type refinements in this case
    */
  val nums: MemberCons {type T = Int} =
    MemberCons(2, MemberCons(3, MemberNil())): MemberCons {type T = Int}

  // Here the reifications are optional and is present to make things clearer
  val m1: Int = nums.head
  val m2: Option[Int] = nums.tail.uncons.map(_.head)

  val m3: Option[Int] = m2.map(_ - m1)

  /**
    Had we ommited the refinement in MemberCons to be

    sealed abstract class MemberCons extends MemberList {self =>
      val head: T
      val tail: MemberList // {type T = self.T}
    }

    we would get

    m3: Option[m1.tail.T] = Some(3)

    and
    m3.map(_ - res2) would not compile because Int.(-) is not a member of nums.tail.T

    In terms of type parameters
    - MemberList is like ParamList[_]
    - MemberList {type T = Future[Event]} is like ParamList[Future[Event]]

    */


  /**
    So, when does member types have an advantage, or at least not a disadvantage?
    */
  def memberListLength(xs: MemberList): Int =
    xs.uncons match {
      case None => 0
      case Some(c) => 1 + memberListLength(c.tail)
    }

  def paramListLength[T](xs: ParamList[T]): Int =
    xs match {
      case ParamNil() => 0
      case ParamCons(_, t) => 1 + paramListLength(t)
    }

  def paramListLengthExistential(xs: ParamList[_]): Int =
    xs match {
      case ParamNil() => 0
      case ParamCons(_, t) => 1 + paramListLengthExistential(t)
    }


  /**
    ##############################
    ##############################

    When are two methods alike?

    A method R is more general than, or as general as Q if Q may be
    implemented by only making a call to R, passing along the arguments

    It follows that R can be invoked in all the situations that Q can be invoked in,
    and more besides.

    we say "R <:m Q"    which denotes that R can be invoked in place of Q for all contexts

    !(R <:m Q) => that there exists a situation where Q calling on R fails

    when (R <:m Q) && !(Q <:m R) => R <m Q

    Lets take a look
    */

  import scala.collection.mutable.ArrayBuffer

  // no compilados
  // def copyToZero(xs: ArrayBuffer[_]): Unit =
  //   xs += xs(0)

  // instead we cheat
  private def copyToZeroParametrized[T](xs: ArrayBuffer[T]): Unit =
    xs += xs(0)

  // Well fug
  def copyToZeroExistential(xs: ArrayBuffer[_]): Unit =
    copyToZeroParametrized(xs)

  /**
    We can show where two methods are not alike
    */
  def paramListDropFirst[T](xs: ParamList[T]): ParamList[T] =
    xs match {
      case ParamNil() => ParamNil()
      case ParamCons(_, t) => t
    }

  def memberListDropFirstT[T0](xs: MemberList {type T = T0})
      : MemberList {type T = T0} =
    xs.uncons match {
      case None => MemberNil()
      case Some(c) => c.tail
    }

  // We can drop the refinements, it still compiles
  def memberListDropFirstE(xs: MemberList): MemberList =
    xs.uncons match {
      case None => MemberNil()
      case Some(c) => c.tail
    }


  /**
    We may implement FirstE with FirstT:
    */

  def memberListEbyT(xs: MemberList): MemberList =
    memberListDropFirstT[xs.T](xs)

  /**
    However there is an important difference, the existential version
    does not relate the arguments type T to the results type T, which
    makes the following method legal
    */
  def memberListBadMethod[T0](xs: MemberList): MemberList =
    MemberCons[Int](42, MemberNil())

  /**
    This is why the converse method is not legal
    */
  // No compilados
  // def memberListTbyE[T0](xs: MemberList {type T = T0})
  //     : MemberList {type T = T0} =
  //   memberListDropFirstE(xs)

  /**
    thus memberListDropFirstT <m memberListDropFirstE

    that is memberListDropFirstT is strictly more general


    Now, java, being a shit language and all, has the following flaw, namely that
    all generics are assumed to be classes (that is, not primitives).
    Why the fuck this is the case I do not intend to find out, but you may not
    use int, only Integer (can Integer be Null? fugg :--DDD)
    */

}

// object ComponentTraitsDI {
// 
//   /**
//     DI for getting users from a user repository
//     */
// 
//   trait UserRepository {
//     def get(id: Int): User
//     def find(username: String): User
//   }
// 
// 
//   /**
//     In the cake pattern we use component traits
//     */
// 
//   trait UserRepositoryComponent {
// 
//     def userRepository: UserRepository
// 
//     trait UserRepository {
//       def get(id: Int): User
//       def find(username: String): User
//     }
//   }
// 
//   /**
//     We then declare the dependency in abstract classes using self-type declarations
//     */
// 
//   trait Users {
//     this: UserRepositoryComponent =>
// 
//     def getUser(id: Int): User = {
//       userRepository.get(id)
//     }
// 
//     def findUser(username: String): User = {
//       userRepository.find(username)
//     }
//   }
// 
//   /**
//     Regular old composition
//     */
//   trait UserInfo extends Users {
//     this: UserRepositoryComponent =>
// 
//     def userEmail(id: Int): String = {
//       getUser(id).email
//     }
// 
//     def userInfo(username: String): Map[String, String] = {
//       val user = findUser(username)
//       val boss = getUser(user.supervisorId)
//       Map(
//         "fullName" -> s"${user.firstName} ${user.lastName}",
//         "email" -> s"${user.email}",
//         "boss" -> s"${boss.firstName} ${boss.lastName}"
//       )
//     }
//   }
// 
//   /**
//     We extend the component trait to provide concrete implementations
//     */
//   trait UserRepositoryComponentImpl extends UserRepositoryComponent {
// 
//     def userRepository = new UserRepositoryImpl
// 
//     class UserRepositoryImpl extends UserRepository {
// 
//       def get(id: Int) = {
//         ???
//       }
// 
//       def find(username: String) = {
//         ???
//       }
//     }
//   }
// 
//   /**
//     We can now create an instance of our class with the dependency
//     by mixing in a concrete implementation
//     */
// 
//   object UserInfoImpl extends
//       UserInfo with
//       UserRepositoryComponentImpl
// 
// }
// 
// object ImplicitsDI {
//   /**
//     We could have achieved something similar using implicits
//     */
// 
//   trait Users {
// 
//     def getUser(id: Int)(implicit userRepository: UserRepository) = {
//       userRepository.get(id)
//     }
// 
//     def findUser(username: String)(implicit userRepository: UserRepository) = {
//       userRepository.find(username)
//     }
//   }
// 
//   object UserInfo extends Users {
// 
//     def userEmail(id: Int)(implicit userRepository: UserRepository) = {
//       getUser(id).email
//     }
// 
//     def userInfo(username: String)(implicit userRepository: UserRepository) = {
//       val user = findUser(username)
//       val boss = getUser(user.supervisorId)
// 
//       Map(
//         "fullName" -> s"${user.firstName} ${user.lastName}",
//         "email" -> s"${user.email}",
//         "boss" -> s"${boss.firstName} ${boss.lastName}"
//       )
//     }
//   }
// 
// }
// 
// object ReaderDI {
// 
//   val triple: Func1[Int,Int] = (i: Int) => i * 3
//   val thricePlus2: Func1[Int,Int] = triple andThen (i => i + 2)
// 
//   val thricePlus2asString: Func1[Int,String] = thricePlus2 andThen (i => i.toString)
// 
//   /**
//     A reader monad has andThen as map over unary functions
// 
//     map[A,B,C](u: Func1[A,B])(f: B => C): Func1[A,C] =
//       u andThen (i: B => f(i))
// 
//     flatMap[A,B,C](a: Func1[A,B])(f: B => Func[B,C]): Func[A,C] =
//       g: A => f(a(g))
// 
// 
//     with scalaz reader we get:
//     */
// 
//   import scalaz.Reader
// 
//   val triple_ = Reader((i: Int) => i * 3)
//   val thricePlus2_ = triple_ map (i => i + 2)
// 
//   val f = for (i <- thricePlus2) yield i.toString
//   // f(3) => "11"
// 
// 
// 
//   trait Users {
// 
//     def getUser(id: Int) = Reader((userRepository: UserRepository) =>
//       userRepository.get(id)
//     )
// 
//     def findUser(username: String) = Reader((userRepository: UserRepository) =>
//       userRepository.fing(username)
//     )
//   }
// 
//   /**
//     Here the we only work Reader[UserRepository, User], not User
//     nothing happens until the actual dependency is injected
//     */
// 
//   object UserInfo extends Users {
// 
//     def userEmail(id: Int): Reader[UserRepository, String] = {
//       getUser(id) map (_.email)
//     }
// 
//     // we can (and should?) omit the reader return signature
//     def userInfo(username: String): Reader[UserRepository, Map[String,String]] =
//       for {
//         user <- findUser(username)
//         boss <- getUser(user.supervisorId)
//       } yield Map(
//         "fullName" -> s"${user.firstName} ${user.lastName}",
//         "email" -> s"${user.email}",
//         "boss" -> s"${boss.firstName} ${boss.lastName}"
//       )
//   }
// 
//   /**
//     Say instead we wanted to add a mail service:
//     */
// 
//   trait Config {
//     def userRepository: UserRepository
//     def mailService: MailService
//   }
// 
//   /**
//     All we'd have to add would be new primitives:
//     */
// 
//   // Very similar to Users
//   trait Users2 {
// 
//     def getUser(id: Int) = Reader((cfg: Config) =>
//       cfg.userRepository.get(id)
//     )
// 
//     def findUser(username: String) = Reader((cfg: Config) =>
//       cfg.userRepository.fing(username)
//     )
//   }
//   /**
//     Bretty huge when we have few underlying primitives and many higher
//     level readers
//     */
// 
// 
// }
