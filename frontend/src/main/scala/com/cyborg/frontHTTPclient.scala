package com.cyborg

import fs2._
import fs2.Stream._
import fs2.util.syntax._

import scala.language.higherKinds

import fs2._

import org.scalajs._

object frontHTTPclient {

  // refactor me
  def startShodanServer: Task[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:9998/connect")
    Task.delay{ req.send() }
  }

  def stopShodanServer: Task[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:9998/stop")
    Task.delay{ req.send() }
  }

  def startAgentServer: Task[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:9998/agent")
    Task.delay{ req.send() }
  }

  def startWfServer: Task[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:9998/wf")
    Task.delay{ req.send() }
  }


}
