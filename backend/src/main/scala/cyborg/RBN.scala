package cyborg

object BooleanNetwork {

  // type Edges = List[List[Int]]
  // type Rules = List[List[Boolean]]

  // val _edges: Edges = List[List[Int]]()
  // val _rules: Rules = List[List[Boolean]]()

  // type Readout = List[Boolean]
  // type Readouts = List[Readout]

  // case class simpleRBN(
  //   state: List[Boolean]
  //   , connectivity: Edges
  //   , rules: Rules){

  //   def update: (simpleRBN, Readout) = {
  //     val next = copy(state = (0 until state.length).toList.map{ index =>
  //       val connections: List[Int] = connectivity(index)
  //       val key: Int = connections.zipWithIndex.map { case (element, index) => {
  //         if(state(element)) 1 << index else 0 }
  //       }.sum
  //       rules(index)(key)
  //     })
  //     (next, next.state)
  //   }

  // }

  // type RBN[+A] = (simpleRBN => (simpleRBN, A))

  // def unit[A](a: A): RBN[A] = (net => (net, a))

  // def map[A,B](n: RBN[A])(f: A => B): RBN[B] = net => {
  //   val (nextNet, res) = n(net)
  //   (nextNet, f(res))
  // }

  // def flatMap[A,B](n: RBN[A])(f: A => RBN[B]): RBN[B] = net => {
  //   val (nextNet, res) = n(net)
  //   f(res)(nextNet)
  // }

  // def modify(f: simpleRBN => simpleRBN): RBN[ Unit ] = net =>
  //   (f(net), ())

  // def get: RBN[simpleRBN] = net => (net, net)

  // def runNet: RBN[ Readout ] =
  //   net => net.update


  // def findAttractor(maxLength: Int): RBN[ Readouts ] = net => {

  //   val seen = scala.collection.mutable.ListBuffer[ Readout ]()

  //   def go: RBN[ Readouts ] =
  //     flatMap(runNet) { res =>
  //       if(seen contains res){
  //         seen += res
  //         unit(seen.toList)
  //       }
  //       else {
  //         seen += res
  //         go
  //       }
  //     }

  //   go(net)
  // }

  // def calculateAttractorLength(readouts: Readouts): Option[Int] = {
  //   val dist = readouts.length - readouts.indexOf(readouts.last)
  //   if (dist == 1)
  //     None
  //   else Some(dist)
  // }

  // def perturb(perturbationPoints: List[(Int, Boolean)]): RBN[ Unit ] = {
  //   def f(rbn: simpleRBN): simpleRBN = {
  //     val perturbed = (rbn.state /: perturbationPoints)((state, λ) =>
  //       λ match {
  //         case (point, value) =>
  //           state.updated(point, value)
  //       })
  //     rbn.copy(state = perturbed)
  //   }
  //   modify(f)
  // }


  // case object simpleRBN {
  //   import scala.util.Random
  //   def apply(n: Int, k: Int, p: Double, init: Option[List[Boolean]]): simpleRBN = {
  //     val indices = (0 to n-1).toList

  //     val (connectivity, rules) = ((_edges, _rules) /: indices)(
  //       (accumulated, id) => accumulated match {
  //         case (rules, connections) => {

  //           val choices = Random.shuffle(indices)
  //           val connection = choices.take(k)
  //           val rule = List.fill(1 << k)(Random.nextBoolean)

  //           (connection :: connections, rule :: rules)

  //         }
  //       }
  //     )
  //     val initState = init.getOrElse( List.fill(n)(Random.nextBoolean) )
  //     simpleRBN(initState, connectivity, rules)
  //   }
  // }

  // def rbnTest: Unit = {

  //   val perturbance1 = List(1 -> true, 4 -> false, 5 -> true)
  //   val perturbance2 = List(2 -> false, 3 -> false, 7 -> true)

  //   def stringify(xs: List[Boolean]): String = ("" /: xs)((µ, λ) => (µ + (if(λ) "#" else " " )))
  //   def stringify2(xs: List[String]): String = ("" /: xs)((µ, λ) => (µ + "\n" + λ))
  //   val net = simpleRBN(30, 2, 0.5, None)

  //   val memes = findAttractor(400)(net)
  //   val memes2 = memes._2.map(stringify(_))
  //   val memes3 = flatMap(perturb(perturbance1)){_: Unit => findAttractor(400)}(memes._1)
  //   val memes4 = memes3._2.map(stringify(_))
  //   println(stringify2(memes2))
  //   println("perturbing")
  //   println(stringify2(memes4))
  // }

  // def sensoryToPerturbance(sensory: List[Double]): List[(Int, Boolean)] = {
  //   List[(Int, Boolean)]()
  // }
}