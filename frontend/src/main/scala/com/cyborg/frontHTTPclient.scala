package com.cyborg

import cats.effect.IO

import org.scalajs._

object frontHTTPclient {

  // refactor me
  def startShodanServer: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/connect")
    req.onload = (e: dom.Event) => {
      println(e)
      println("nice meme..")
      println(req.statusText)
    }
    IO.apply{ req.send() }
  }

  def stopShodanServer: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/stop")
    IO.apply{ req.send() }
  }

  def startAgentServer: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/agent")
    IO.apply{ req.send() }
  }

  def startWfServer: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/wf")
    IO.apply{ req.send() }
  }

  def runFromDB: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/db")
    IO.apply{ req.send() }
  }

  def crashSHODAN: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/fuckoff")
    IO.apply{ req.send() }
  }

  def dspTest: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/dsptest")
    IO.apply{ req.send() }
  }

  def dspSet: IO[Unit] = {
    val req = new dom.XMLHttpRequest()
    req.open("POST", "http://127.0.0.1:8080/dspset")
    IO.apply{ req.send() }
  }
}
