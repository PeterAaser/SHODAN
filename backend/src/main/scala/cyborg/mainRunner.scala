// package cyborg

// import cyborg.wallAvoid.Agent
// import com.typesafe.config.ConfigFactory
// import fs2._
// import MEAMEutilz._


// import fs2.Stream._
// import fs2.async.mutable.Queue
// import fs2.util.Async
// import fs2.io.tcp._

// import wallAvoid.Agent

// import scala.language.higherKinds

// import httpCommands._
// import backendImplicits._
// import utilz._

// object mainRunner {

//   import backendImplicits._

//   case class MEAMEstate(
//     online: Boolean,
//     configured: Boolean,
//     running: Boolean)

//   case class SHODANstate(
//     running: Boolean,

//     // maybe do it like this?
//     sourceReady: Boolean,
//     source: Option[Stream[Task,Int]]

//   )

//   def SHODANrunner(λ: Stream[Task, userCommand]): Stream[Task,Unit] = {

//     val topics = Assemblers.assembleTopics[Task]
//     val agentSink = wsIO.webSocketServerAgentObserver
//     val meameFeedbackSink: Sink[Task,Byte] = (λ: Stream[Task,Byte]) => λ.drain

//     topics flatMap {
//       topics => {
//         val paipu = staging.commandPipe(
//           topics._1,
//           topics._2,
//           agentSink,
//           meameFeedbackSink
//         )
//         concurrent.join(100)(λ.through(paipu))
//       }
//     }
//   }

//   def httpRunner: Task[Unit] = {

//     val userCommandQueueTask = async.unboundedQueue[Task,userCommand]
//     val what = Stream.eval(userCommandQueueTask) flatMap {
//       queue => {
//         Stream.eval(httpServer.startServer(queue.enqueue)) merge
//         queue.dequeue.through(SHODANrunner)
//       }
//     }
//     what.run
//   }

//   def testRunner: Task[Unit] = {
//     val commands: Stream[Task, userCommand] = Stream(StartMEAME, AgentStart, StartWaveformVisualizer)
//     SHODANrunner(commands).run
//   }
// }
