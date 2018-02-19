package cyborg

import utilz._
import HttpClient._
import fs2._
import cats._
import cats.effect._
import DspRegisters._


object DspLog {

  // log ID -> name map
  val logMap = Map(
    1  -> "DAC_STATE_CHANGE",
    2  -> "CONF",
    3  -> "CONF_RESET",
    4  -> "CONF_START",
    5  -> "TRIGGER",
    6  -> "STATE_EN_ELEC",
    7  -> "STATE_DAC_SEL",
    8  -> "STATE_MODE",
    9  -> "BOOKING",
    10 -> "READ_STIM",
    11 -> "COMMS_READ_REQ",
    12 -> "BOOKING_FOUND",
    13 -> "STIMULUS_WRITE")



  def parseStimulus(logEntry: List[Int]): String =
    s"stimulus entry:\n ${logEntry(0)} <- ${logEntry(1)}\tstimulus.c: ${logEntry(2)}"

  val logHandlers = Map(
    13 -> parseStimulus _
  )


  def getDspLog: IO[RegisterReadResponse] =
    for {
      items <- readRegistersRequest(RegisterReadList( List(LOG_ENTRIES)) )
      resp  <- readRegistersRequest(RegisterReadList( (0 until items.values.head).map(z => (z*4 + LOG_START)).toList ))
    } yield (resp)


  def readLogEntry(log: List[Int]): (List[Int], String) = {

    // Reads the head of the log and peels away one log entry based on it.
    def chomp(log: List[Int]): (List[Int], List[Int]) =
      (log.tail.take(log.head), log.tail.drop(log.head))

    def parseEntry(entry: List[Int]): String = {
      logHandlers(entry.head)(entry.tail)
    }

    val (head, remainder) = chomp(log)
    (remainder, parseEntry(head))
  }


  def readLog(log: List[Int]): List[String] = {
    def helper(log: List[Int], descriptions: List[String]): List[String] = log match {
      case h::t => {
        val (remainder, description) = readLogEntry(log)
        helper(remainder, description :: descriptions)
      }
      case _ => descriptions
    }

    helper(log, List[String]())
  }


  def printLog(log: List[Int]): String = {
    val entries = readLog(log)
    s"DSP log with ${entries.size} entries:" + entries.mkString("\n", "\n------\n\n", "\n")
  }


  def printDspLog: IO[Unit] = for {
    entries <- getDspLog
    _       <- IO { say(printLog(entries.values)) }
  } yield ()
}
