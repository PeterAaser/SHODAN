package cyborg

import cats.effect.concurrent.Deferred

import cyborg.RPCmessages._

sealed trait UserCommand
case object StartMEAME extends UserCommand
case object StopMEAME  extends UserCommand

case object StopData   extends UserCommand

case object AgentStart extends UserCommand
case object AgentStop  extends UserCommand

case class  RunFromDB(info: RecordingInfo)                           extends UserCommand
case object DBstartRecord                                            extends UserCommand
case object DBstopRecord                                             extends UserCommand

// TODO Fails in entrypoint cause effects
case class GetSHODANstate[F[_]](ret: Deferred[F,EquipmentState])     extends UserCommand
case class GetRecordings[F[_]](ret: Deferred[F,List[RecordingInfo]]) extends UserCommand

case object Shutdown                                                 extends UserCommand

// Not that relevant now
case object DspSet  extends UserCommand
case object DspConf extends UserCommand

case object DspStimTest   extends UserCommand
case object DspUploadTest extends UserCommand // uploading stimulus, not bitfile
case object DspBarf       extends UserCommand
case object DspDebugReset extends UserCommand
