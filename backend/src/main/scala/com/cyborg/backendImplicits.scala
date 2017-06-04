package com.cyborg

import java.nio.channels.AsynchronousChannelGroup
import fs2._

object backendImplicits {
  implicit val tcpACG : AsynchronousChannelGroup = namedACG.namedACG("tcp")
  implicit val strategy: fs2.Strategy = fs2.Strategy.fromFixedDaemonPool(16, threadName = "fugger")
  implicit val scheduler: Scheduler = fs2.Scheduler.fromFixedDaemonPool(16)
}
