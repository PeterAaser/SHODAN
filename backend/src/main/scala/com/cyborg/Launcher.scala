package com.cyborg

import com.cyborg.jetty.ApplicationServer


object Launcher {
  def main(args: Array[String]): Unit = {
    val server = new ApplicationServer(8080, "backend/target/UdashStatic/WebContent")
    server.start()

    if(doobieTasks.superdupersecretPassword != ""){
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")

      println("WEEE WOOO WEEE WOOO\n")
      println("THE FUCKIN PASSWORD IS HARDCODED YOU DUNCE")
      println("\nWEEE WOOO WEEE WOOO")

      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      println("################################################")
      for(i <- 0 until 2){ 2/(1-i) }
    }


    println(">tfw too intelligent for real tests")
    // val toStimFreq =
    //   MEAMEutilz.toStimFrequency(List(1, 2, 3, 4, 5), MEAMEutilz.logScaleBuilder(scala.math.E))
    // val freqs = toStimFreq(List(10000.0, 4000.0, 1000.0, 100.0, 3000.0, 0.1))
    // println(freqs)

    // database.meme.foreach(println)
    // println("------------")
    // database.meme2.foreach(println)
    // println("------------")
    // database.meme3.foreach(println)
    // println("------------")
    // println(database.DELET_THIS)
    // println("------------")
    // println(database.DELEET.unsafePerformIO)
    // println("------------")
    // println(database.shrieks.unsafePerformIO)
    // println("------------")
    // database.getDb.unsafePerformIO.foreach(println)
    // println("-----------4")
    // // println(database.theMany)
    // println(database.theMany2.unsafePerformIO)
    // println("------------")
    // database.getDb.unsafePerformIO.foreach(println)
    // println("------------")

    // no idea mang, maybe not supported with psql?
    // println(database.up.unsafePerformIO)

  }
}
