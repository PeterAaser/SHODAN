package cyborg

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import params._
import fs2._
import fs2.concurrent.Signal
import cats.effect._
import utilz._
import backendImplicits._
import java.nio.file.Paths
import java.io.{ObjectOutputStream, FileOutputStream,
  ObjectInputStream, FileInputStream}


object RBN {
  type State         = List[Boolean]
  type Node          = Int
  type Edges         = List[List[Node]]
  type Perturbation  = (Node, Boolean)
  type Rule          = List[Boolean]


  val resourceDir = Paths.get(".").toAbsolutePath +
    "/backend/src/main/resources/RBN/"


  /**
    * Serialize the configuration of an RBN to disk.
    */
  def serialize(rbn: RBN, filePath: String): IO[Unit] = {
    val oos = new ObjectOutputStream(new FileOutputStream(filePath))
    oos.writeObject(rbn)
    oos.close

    IO.unit
  }


  /**
    * Deserialize an already initialized RBN from disk.
    */
  def deserialize(filePath: String): IO[RBN] = {
    val ois = new ObjectInputStream(new FileInputStream(filePath))
    val rbn = ois.readObject.asInstanceOf[RBN]
    ois.close

    IO(rbn)
  }
}


import RBN._
case class RBN(
  state: State,
  edges: Edges,
  rules:  List[Rule],
  lowFreq: Double  = 3.0,
  highFreq: Double = 8.0
) extends Serializable {
  /**
    * Neighbors are defined as the edges _into_ the node in
    * question, which may be confusing if thought of in CA terms.
    */
  def neighborStates(node: Node): List[Boolean] = {
    edges(node).map(neighbor => state(neighbor))
  }


  /**
    * Not exactly a fast way to achieve this -- maybe consider using
    * Ints as states.
    */
  def sumNeighborStates(node: Node): Int = {
    val powers = (0 until edges.head.length).map(math.pow(2, _).toInt)
    val states = neighborStates(node).map(b => if (b) 1 else 0)
    (powers, states).zipped.map(_ * _).sum
  }


  def step: RBN = {
    copy(state = state.zipWithIndex.map{t =>
      rules(t._2)(sumNeighborStates(t._2))
    })
  }


  /**
    * Steps the RBN with input in the form of perturbations.
    */
  def step(perturbations: List[Perturbation]): RBN = {
    perturb(perturbations).step
  }


  /**
    * Applies a perturbation to the RBN, given as a list of nodes
    * and the new state.
    */
  def perturb(perturbations: List[Perturbation]): RBN = {
    copy(state = perturbations.foldLeft(state){(s, p) =>
      s.updated(p._1, p._2)
    })
  }


  /**
    * Perturbs a list of nodes by flipping their values within the
    * RBN. Ignores previous values.
    */
  def perturbNodes(perturbations: List[Node]): RBN = {
    copy(state = perturbations.foldLeft(state){(s, n) =>
      s.updated(n, !s(n))
    })
  }


  /**
    * Print RBN state in the standard, trajectory way, where time
    * flows downwards the lattice.
    */
  def printStateANSI: Unit = {
    state.map{s =>
      if (s) {
        print(s"${Console.WHITE_B} ${Console.RESET}")
      } else {
        print(s"${Console.BLACK_B} ${Console.RESET}")
      }
    }
    println
  }


  /**
    * Finds an attractor. Does not output the basin leading to the
    * given attractor, i.e. you'll only get the actual cycle.
    */
  def attractor(maxLength: Int): Option[List[State]] = {
    def go(seen: List[State], rbn: RBN, depth: Int): Option[List[State]] = {
      (seen, depth) match {
        case (_, 0) => None
        case (seen, _) if seen contains rbn.state =>
          Some(rbn.state +: seen.takeWhile(_ != rbn.state) ::: List(rbn.state))
        case _ => go(rbn.state +: seen, rbn.step, depth-1)
      }
    }

    go(List(), this, maxLength).map(_.reverse)
  }
}
