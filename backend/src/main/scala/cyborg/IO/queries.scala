package cyborg

import doobie.imports._
import doobie.postgres.imports._

import fs2._

import com.github.nscala_time.time.Imports._

object queries {

  import doobieTasks._

  // def getChannels(experimentId: Int): ConnectionIO[List[ChannelRecording]] =
  def getChannels(experimentId: Int): ConnectionIO[List[ChannelRecording]] =
  sql"""
       SELECT experimentId, channelRecordingId, channelNumber
       FROM channelRecording
       WHERE channelRecordingId = $experimentId
     """.query[ChannelRecording].list
}
