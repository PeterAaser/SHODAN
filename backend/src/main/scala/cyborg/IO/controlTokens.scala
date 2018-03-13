package cyborg


object ControlTokens {

  sealed trait UserCommand
  case object StartMEAME extends UserCommand
  case object StopMEAME  extends UserCommand

  case object StopData   extends UserCommand

  case object AgentStart extends UserCommand
  case object AgentStop  extends UserCommand

  // case object StartWaveformVisualizer extends UserCommand
  // case object ConfigureMEAME extends UserCommand

  case class  RunFromDB(experimentId: Int) extends UserCommand
  case object RunFromDBNewest              extends UserCommand
  case object DBstartRecord                extends UserCommand
  case object DBstopRecord                 extends UserCommand

  case object Shutdown                     extends UserCommand

  // Not that relevant now
  case object DspSet  extends UserCommand
  case object DspConf extends UserCommand

  case object DspStimTest   extends UserCommand
  case object DspUploadTest extends UserCommand // uploading stimulus, not bitfile
  case object DspBarf       extends UserCommand
  case object DspDebugReset extends UserCommand
}
