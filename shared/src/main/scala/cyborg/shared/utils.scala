package cyborg

object bonus {

  implicit class MQueueOps[A](xs: scala.collection.mutable.Queue[A]) {
    def dequeue_(): Unit = {val _ = xs.dequeue; ()}
  }

  implicit class SeqOps[A](xs: Seq[A]) {
    def zipWith[B,C](ys: Seq[B])(f: (A,B) => C): Seq[C] =
      xs.zip(ys) map f.tupled

    def mapWithIndex[B](f: (A, Int) => B): Seq[B] =
      xs.zipWithIndex.map(x => f(x._1, x._2))

    def zipIndexLeft: Seq[(Int, A)] = xs.zipWithIndex.map{ case(a,b) => (b,a) }

    def minByOption(implicit ev: Ordering[A]): Option[A] =
      if (xs.isEmpty) None
      else Some(xs.min)

    def decimate(dropEvery: Int): Seq[A] =
      xs.grouped(dropEvery).map(_.head).toSeq
  }

  implicit class DoubleBonusOps(d: Double) {
    def isInRange(lower: Double, upper: Double): Boolean =
      (d >= lower) && (d <= upper)
  }

  // Although the fundamental theorem of engineering states e = pi = 3 we go with the real deal here.
  def epow(d: Double): Double = scala.math.pow(scala.math.E, d)

  import cats._
  import cats.implicits._
  import cats.Semigroup
  import cats.Monoid
  def minSG[A: Order]: Semigroup[A] = new Semigroup[A] {
    def combine(x: A, y: A): A = if((x compare y) > 0) y else x
  }
  def minMonoid[A: Order](min: A): Monoid[A] = new Monoid[A] {
    def empty: A = min
    def combine(x: A, y: A): A = if((x compare y) > 0) y else x
  }

  implicit class OptionBonusOps(p: Option.type){
    def when[A](p: Boolean)(a: A) = if(p) Some(a)  else None
    def when_(p: Boolean)         = if(p) Some(()) else None
  }

  implicit class MapOps[K,V](m: Map[K,V]) {
    def intersectKeys[V2](that: Map[K,V2]): Map[K,(V,V2)] = {
      for {
        (a, b) <- m
        c <- that.get(a)
      } yield {
        a -> (b,c)
      }
    }

    def apply(s: Seq[K]): Map[K,V] =
      s.map(x => m.get(x).map(y => (x, y))).flatten
        .toMap


    def applySeq(s: Seq[K]): Map[K,V] =
      s.map(x => m.get(x).map(y => (x, y))).flatten
        .toMap

    def updateAt(k: K)(f: V => V): Map[K,V] =
      m.updated(k, f(m(k)))
  }

  type mV = Double

  def swapMap[A,B](m: Map[A,B]): Map[B,List[A]] =
    m.toList.groupBy(_._2).mapValues(_.map(_._1))


  def intersectWith[A, B, C, D](m1: Map[A, B], m2: Map[A, C])(f: (B, C) => D): Map[A, D] =
    for {
      (a, b) <- m1
      c <- m2.get(a)
    } yield a -> f(b, c)

  def intersect[A, B, C, D](m1: Map[A, B], m2: Map[A, C]): Map[A, (B,C)] =
    for {
      (a, b) <- m1
      c <- m2.get(a)
    } yield a -> (b, c)


  implicit class IntOps(i: Int) {
    def asBinary(size: Int = 0): String = {
      val l  = i.toBinaryString
      val padLen = if(size == 0) 0 else size - l.size
      val pad = ("" /: (0 until padLen).map(_ => "0"))(_+_)
      pad + l
      i.toBinaryString
    }

    def asBinarySpaced: String = {
      val l  = i.toBinaryString
      val padLen = 32 - l.size
      val pad = ("" /: (0 until padLen).map(_ => "0"))(_+_)
      val s = pad + l
      s.take(8).grouped(2).map(_ + " ").toList.mkString + " " +
        s.drop(8).take(8).grouped(2).map(_ + " ").toList.mkString + " " +
        s.drop(16).take(8).grouped(2).map(_ + " ").toList.mkString + " " +
        s.drop(24).take(8).grouped(2).map(_ + " ").toList.mkString + " "
    }

    def asBinarySpaced2: String = {
      i.toBinaryString.grouped(2).map(_ + " ").toList.mkString("")
    }

    def getField(msb: Int, size: Int): Int = {
      val ls = i << (31 - msb)
      val rs = ls >> ((31 - size) + 1)
      val mask = (1 << size) - 1
      rs & mask
    }

    def getBit(bit: Int): Boolean = {
      1 == ((i >> bit) & 1)
    }


    // don't be a dumbass and use a too big fieldValue
    def setField(msb: Int, nBits: Int, fieldValue: Int): Int = {
      val field = (fieldValue << (msb - nBits))
      val mask = (1 << nBits) - 1
      val shiftedMask = (mask << (msb - nBits))
      val masked = ~((~i) | shiftedMask);

      field | masked
    }

    def clamp(min: Int, max: Int): Int = if(i < min) min else if(i > max) max else i

    def %%(that: Int): Int = if(i > 0) i % that else (i % that) + that
  }

  // class mutableBoundedQueue[A](repr: Array[A]){
  //   var first = 0
  //   var last = 0

  //   def take
  // }
}
