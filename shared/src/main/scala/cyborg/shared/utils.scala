package cyborg

object bonus {

  implicit class SeqOps[A](xs: Seq[A]) {
    def zipWith[B,C](ys: Seq[B])(f: (A,B) => C): Seq[C] =
      xs.zip(ys) map f.tupled

    def mapWithIndex[B](f: (A, Int) => B): Seq[B] =
      xs.zipWithIndex.map(x => f(x._1, x._2))

    def zipIndexLeft: Seq[(Int, A)] = xs.zipWithIndex.map{ case(a,b) => (b,a) }
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

    def apply(s: Seq[K]): Map[K,V] = {
      s.map(x => m.get(x).map(y => (x, y))).flatten
        .toMap
    }

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

}
