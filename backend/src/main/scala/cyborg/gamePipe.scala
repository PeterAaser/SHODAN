package cyborg

import cats.effect.{ Concurrent }
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

import utilz._

object agentPipe {

  type ffANNinput = Vector[Double]
  type ffANNoutput = List[Double]

  import fs2._
  import wallAvoid._
  import wallAvoid.Agent._

  import cyborg.wallAvoid._

  val initAgent = {
    import params.game._
    Agent(Coord(( width/2.0), ( height/2.0)), 0.0, 90)
  }


  def wallAvoidancePipe[F[_]](init: Agent = initAgent): Pipe[F, ffANNoutput, Agent] = {

    def go(agent: Agent, s: Stream[F,ffANNoutput]): Pull[F, Agent, Unit] = {
      s.pull.uncons1 flatMap {
        case Some((input, tl)) => {
          val nextAgent = updateAgent(agent, input)
          Pull.output1(agent) >> go(nextAgent, tl)
        }
        case None => Pull.done
      }
    }

    in => go(init, in).stream
  }


  /**
    Sets up 5 challenges, evaluates ANN performance and returns
    the evaluation via the eval sink
    */
  def evaluatorPipe[F[_]: Concurrent](
    ticksPerEval: Int,
    evalFunc: Double => Double,
    evalSink: Sink[F,Double]): Pipe[F,ffANNoutput,Agent] = {


    say("running evaluatorPipe")

    /**
      Runs an agent through ticksPerEval ticks, recording the closest it was a wall
      and halts
      */
    def challengeEvaluator(agent: Agent): Pipe[F,ffANNoutput,Agent] = {

      def go(ticks: Int, agent: Agent, s: Stream[F,ffANNoutput]): Pull[F,Agent,Unit] = {
        s.pull.uncons1 flatMap {
          case Some((agentInput, tl)) => {
            val nextAgent = Agent.updateAgent(agent, agentInput)
            if (ticks > 0){
              Pull.output1(nextAgent) >> go(ticks - 1, nextAgent, tl)
            }
            else {
              Pull.output1(nextAgent) >> Pull.done
            }
          }
          case _ => Pull.done
        }
      }
      in => go(ticksPerEval, agent, in).stream
    }

    def evaluateRun: Pipe[F,Agent,Double] = {
      def go(s: Stream[F,Agent]): Pull[F,Double,Unit] = {
        s.pull.unconsN(ticksPerEval, false) flatMap {
          case Some((chunk, _)) => {
            val closest = chunk
              .map(_.distanceToClosest)
              .toList
              .min

            Pull.output1(closest) >> Pull.done
          }
          case _ => {
            Pull.done
          }
        }
      }
      in => go(in).stream
    }


    // attaches an evaluator to a joined pipe of n experiments
    def attachSink(
      experimentPipe: Pipe[F,ffANNoutput,Agent],
      evalSink: Sink[F,Double]): Pipe[F,ffANNoutput,Agent] = s => {

      val t = s.through(experimentPipe)
      t.observeAsync(100)(λ =>
        λ.through(evaluateRun).fold(.0)(_+_).through(_.map(evalFunc(_)))
          .through(evalSink)
      )
    }

    // Creates five initial agents, each mapped to a pipe
    val challenges: List[Agent] = createChallenges
    val challengePipes: List[Pipe[F,ffANNoutput,Agent]] = challenges.map(challengeEvaluator(_))

    // Joins the five challenges, attaches an evaluator to the joined pipe
    val challengePipe: Pipe[F,ffANNoutput,Agent]
      = Pipe.join(Stream.emits(challengePipes.map(attachSink(_, evalSink))))

    s: Stream[F,ffANNoutput] => s.through(challengePipe)
  }
}
