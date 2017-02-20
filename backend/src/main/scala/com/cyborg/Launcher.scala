package com.cyborg

import com.cyborg.jetty.ApplicationServer

import fs2._

object Launcher {
  def main(args: Array[String]): Unit = {
    val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
    server.start()
    println("Good meme!")
  }
}
