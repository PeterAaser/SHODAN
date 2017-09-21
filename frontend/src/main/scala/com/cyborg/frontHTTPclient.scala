package com.cyborg

import cats.effect.IO

import org.scalajs._

object frontHTTPclient {

  // refactor me
  def startShodanServer: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:9998/connect")
    IO.apply{ req.send() }
  }

  def stopShodanServer: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:9998/stop")
    IO.apply{ req.send() }
  }

  def startAgentServer: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:9998/agent")
    IO.apply{ req.send() }
  }

  def startWfServer: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:9998/wf")
    IO.apply{ req.send() }
  }


}
