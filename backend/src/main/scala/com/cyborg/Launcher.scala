package com.cyborg

import com.cyborg.jetty.ApplicationServer


object Launcher {
  def main(args: Array[String]): Unit = {


    // val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
    // server.start()
    // mainLoop.outerT.unsafeRun

    println("wello")

    val testList = seqUtils.ScoredSeq(Vector((0.3, "hi"), (0.4, "how"), (0.01, "are"), (0.98, "you"), (1.12, "?")))
    println(testList)
    println(testList.scoreSum)
    println(testList.sort)
    println(testList.normalize)
    println(testList.rouletteScale)

    val rouletteScaled = testList.rouletteScale

    println("--------------------")
    println(rouletteScaled.biasedSample(3))
    println("--------------------")
    println(rouletteScaled.biasedSample(3))
    println("--------------------")
    println(rouletteScaled.biasedSample(3))
    println("--------------------")
    println(rouletteScaled.biasedSample(3))
    println("--------------------")

  }
}
