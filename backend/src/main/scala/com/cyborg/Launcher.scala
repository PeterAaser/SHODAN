package com.cyborg

import com.cyborg.jetty.ApplicationServer
import com.typesafe.config._

import fs2._

object Launcher {
  def main(args: Array[String]): Unit = {
    val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
    server.start()
    println("Good meme!")
    val conf = ConfigFactory.load()
    val meme = conf.getInt("test1.meme1")
    val meme2 = conf.getConfig("test3.nesting")
    println(meme2.getString("testerino"))
    println(meme)

    val localConf = conf.getConfig("netConf.local")
    println(localConf.getString("ip"))

    val experiment = conf.getConfig("experimentSetup")
    println(experiment.getIntList("electrodes"))
    println(experiment.getIntList("electrodes"))
  }
}
