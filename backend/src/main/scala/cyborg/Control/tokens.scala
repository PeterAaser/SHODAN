package cyborg

import cats.effect.IO
import cats.effect.concurrent.Deferred

import cyborg.RPCmessages._

sealed trait UserCommand
case class  RunFromDB(info: RecordingInfo) extends UserCommand
case object RunLive extends UserCommand

case object MazeStart extends UserCommand
case object MazeStop  extends UserCommand

case object DBstartRecord extends UserCommand
case object DBstopRecord  extends UserCommand
