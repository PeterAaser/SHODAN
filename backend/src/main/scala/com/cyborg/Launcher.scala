package com.cyborg

import com.cyborg.jetty.ApplicationServer
import com.typesafe.config._

import fs2._

object Launcher {
  def main(args: Array[String]): Unit = {
    val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
    server.start()


    println(">tfw too intelligent for real tests")
    val toStimFreq = MEAMEutilz.toStimFrequency(List(1, 2, 3, 4, 5), MEAMEutilz.logScaleBuilder(scala.math.E))
    val freqs = toStimFreq(List(10000.0, 4000.0, 1000.0, 100.0, 3000.0, 0.1))
    println(freqs)
  }
}
