package cyborg.shared.rpc.server


import io.udash.rpc._
import scala.concurrent.Future

import cyborg._
import cyborg.shared.rpc._
import RPCmessages._
import Setting._
import sharedImplicits._

// The methods the frontend can call from the backend
@RPC
trait MainServerRPC {

  def registerWaveform: Unit
  def unregisterWaveform: Unit
  def registerAgent: Unit
  def unregisterAgent: Unit

  def getSHODANstate: Future[EquipmentState]

  def getRecordings: Future[List[RecordingInfo]]

  def startPlayback(rec: RecordingInfo): Unit

  // TODO: Should contain some info
  def startRecording: Unit
  def stopRecording: Unit

  def startLive: Unit

  def flashDsp: Unit
  def runDspTest(testNo: Int): Unit
  def stopDspTest: Unit
}

object MainServerRPC extends DefaultServerUdashRPCFramework.RPCCompanion[MainServerRPC]
