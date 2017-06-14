package com.cyborg


object Launcher {
  def main(args: Array[String]): Unit = {

    println("########################################")
    println("########################################")
    println("########################################\n\n")
    params.experiment.printMe()
    println("\n\n----\n\n")
    params.filtering.printMe()
    println("\n\n----\n\n")
    params.waveformVisualizer.printMe()
    println("\n\n########################################")
    println("########################################")
    println("########################################")

    import params.experiment._
    staging.runFromHttp(10000, List(3, 6, 9, 12)).unsafeRun()

    println("wello")

  }
}
