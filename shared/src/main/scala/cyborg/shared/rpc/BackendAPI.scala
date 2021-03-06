package cyborg.shared.rpc.server


import io.udash.rpc._
import scala.concurrent.Future

import cyborg._
import cyborg.shared.rpc._
import RPCmessages._
import Settings._
import State._
import sharedImplicits._

// The methods the frontend can call from the backend
trait MainServerRPC {

  // We only really want to register once.
  def register   : Future[Unit]
  def unregister : Unit

  // If we set to running but something explodes
  def setSHODANstate(s: ProgramState)   : Future[Unit]
  def setSHODANconfig(c: FullSettings)  : Future[Unit]

  def getRecordings(): Future[List[RecordingInfo]]

  def startAgent: Unit

  def selectLargeChannel(c: Int) : Future[Unit]
  def setDownscalingFactor(i: Int) : Future[Int]

  def setChannelTimeSpan(i: Int) : Future[Int]
}

object MainServerRPC extends DefaultServerUdashRPCFramework.RPCCompanion[MainServerRPC]
