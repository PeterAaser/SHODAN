package com.cyborg

import fs2.Strategy
import fs2.Scheduler

object frontendImplicits {
  implicit val strategy: Strategy  = fs2.Strategy.default
  implicit val scheduler: Scheduler = fs2.Scheduler.default
}
