package com.cyborg


object Launcher {
  def main(args: Array[String]): Unit = {


    // import params.experiment._
    staging.runFromHttp2(10000, List(3, 6, 9, 12)).unsafeRun()

    println("wello")

  }
}
