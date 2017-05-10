package com.cyborg

import com.typesafe.config._
import collection.JavaConverters._

object params {


  case class NeuroDataParams(
    sampleRate: Int
      , DAQchannels: List[Int]
      , electrodes: List[Int]
      , sweepSize: Int
  )
  object NeuroDataParams {
    def apply(c: Config): NeuroDataParams = {

      val d = c.getIntList("DAQchannels").asScala.toList.map(_.toInt)
      val e = c.getIntList("electrodes").asScala.toList.map(_.toInt)

      NeuroDataParams(c.getInt("sampleRate"), d, e, c.getInt("sweepSize"))
    }
  }
}
