package cyborg


object RBNassembly {

  // import BooleanNetwork._

  // def assembleRBN[F[_]](observerPipe: Pipe[F, Readouts, Readouts]):
  //     Pipe[F, List[Double], Readouts] = {

  //   val reservoir = simpleRBN(40, 2, 0.5, None)

  //   val FF = Filters.FeedForward(
  //     List(2, 3, 2)
  //       , List(0.1, 0.2, 0.4, 0.0, 0.3)
  //       , List(0.1, -0.2, 0.2, -0.1, 2.0, -0.4, 0.1, -0.2, 0.1, 1.0, 0.3, 0.3))

  //   def RBNPipe[F[_]]: Pipe[F, List[Double], Readouts] = {

  //     def go(net: simpleRBN): Handle[F, List[Double]] => Pull[F, Readouts, Unit] = h => {
  //       h.receive1 {
  //         case (input, h) => {
  //           val perturbations = sensoryToPerturbance(input)
  //           val perturbator = perturb(perturbations)

  //           val (next, readouts) = flatMap(perturbator){_ => findAttractor(400)}(net)

  //           Pull.output1(readouts) >> go(next)(h)
  //         }
  //       }
  //     }
  //     _.pull(go(reservoir))
  //   }

  //   _.through(RBNPipe).through(observerPipe)
  // }

}
