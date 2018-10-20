package cyborg

object bonus {

  implicit class SeqOps[A](xs: Seq[A]) {
    def zipWith[B,C](ys: Seq[B])(f: (A,B) => C): Seq[C] =
      xs.zip(ys) map f.tupled
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
