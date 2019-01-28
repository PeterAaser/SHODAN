package cyborg

import cats.effect.IO
import cats.effect.concurrent.Deferred

import cyborg.RPCmessages._

sealed trait UserCommand
case object ShoutAtOla  extends UserCommand
case object Start       extends UserCommand
case object Stop        extends UserCommand
case object StartRecord extends UserCommand
case object StopRecord  extends UserCommand
