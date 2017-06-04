package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.Async
import fs2.util.syntax._

import scala.language.higherKinds

import fs2.Strategy
import fs2.Scheduler
import fs2._

import org.scalajs._

import scala.util.{Failure, Success}
import fr.hmil.roshttp.response.SimpleHttpResponse
import fr.hmil.roshttp.HttpRequest

object frontHTTPclient {

  implicit val IntCodec = scodec.codecs.int32
  implicit val IntVectorCodec = scodec.codecs.vectorOfN(scodec.codecs.int32, scodec.codecs.int32)
  implicit val StringCodec = scodec.codecs.utf8_32

  // hardcoded
  val phoneHome = "129.241.201.110"
  val SHODANport = 9998 // we're not an open server, so we don't use the regular http port.


  def pingShodanServer: Task[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST",
             "http://127.0.0.1:9998/"
    )
    req.setRequestHeader("Access-Control-Allow-Origin", "*")
    Task.delay{
      req.send()
    }
  }


}
