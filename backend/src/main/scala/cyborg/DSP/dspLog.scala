package cyborg

import utilz._
import HttpClient._
import fs2._
import cats._
import cats.effect._
import DspRegisters._


object DspLog {

  def parseStimulus(logEntry: List[Int]): String =
    s"stimulus entry:\n 0x${logEntry(0).toHexString.toUpperCase()} <- 0x${logEntry(1).toHexString.toUpperCase()} - 0b${BitDrawing.as32BinarySpaced(logEntry(1))}\tstimulus.c: ${logEntry(2)}\n-------\n\n"

  def parseSQbooking(logEntry: List[Int]): String =
    s"stim queue BOOKING:\n" +
      s"at timestep ${logEntry(2)} stim group ${logEntry(0)} booked DAC pair ${logEntry(1)}"

  def parseSQstateChange(logEntry: List[Int]): String = {
    val stateDescription =
      if(logEntry(0) == 0) "IDLE"
      else if(logEntry(0) == 1) "PRIMED"
      else "FIRING"

    s"stim queue STATE CHANGE:\n" +
      s"at timestep ${logEntry(2)} DAC pair ${logEntry(0)} changed state to ${stateDescription}"
  }

  def parseSQconfReset(logEntry: List[Int]): String =
    s"stim queue CONF RESET:\n" +
      s"at timestep ${logEntry(1)} DAC pair ${logEntry(0)} got a conf reset"

  def parseSQconfStart(logEntry: List[Int]): String =
    s"stim queue CONF START:\n" +
      s"at timestep ${logEntry(2)} DAC pair ${logEntry(0)} was configured to stim group ${logEntry(1)}"


  def parseInstructionReceived(logEntry: List[Int]): String =
    s"comms received instruction\n" +
      s"instruction type was ${logEntry(0)}, top instruction id was ${logEntry(1)}"


  def parseSQstates(logEntry: List[Int]): String = {

    val electrodes = logEntry.take(2).zipWithIndex.map{ case(entry,idx) =>
      s"electrodes $idx state ${BitDrawing.as32BinarySpaced(entry)}"}

    def parseDACstate(word: Int): String = BitDrawing.as32BinarySpaced(word: Int)

    val DACs = logEntry.drop(2).take(4).zipWithIndex.map{ case(entry,idx) =>
      s"DAC $idx state: ${parseDACstate(entry)}"}

    def parseElectrodeMode(word: Int): String = BitDrawing.as32BinarySpaced(word)

    val modes = logEntry.drop(6).take(4).zipWithIndex.map{ case(entry,idx) =>
      s"DAC $idx mode: ${parseElectrodeMode(entry)}"}

    s"stim queue STATE DUMP:\n" +
      s"at timestep ${logEntry.last} the state of stim related registers looks like\n\n" +
      "electrodes:\n" +
      electrodes.mkString("","\n","\n\n") +
      "DACs:\n" +
      DACs.mkString("","\n","\n\n") +
      "DAC modes:\n" +
      modes.mkString("","\n","\n")
  }

  def parseSQtriggerFired(logEntry: List[Int]): String =
    s"stim queue TRIGGER FIRED\n" +
      s"at timestep ${logEntry(2)} DAC pair ${logEntry(0)} triggered trigger ${logEntry(1)}"

  def parseSQcanary(logEntry: List[Int]): String =
    s"stim queue CANARY\n" +
      s"confirming sq liveness at ${logEntry(0)} timesteps"

  def unnamed(logEntry: List[Int]): String =
    s"unnamed call with value ${logEntry(0)}"


  def parseSGstate(logEntry: List[Int]): String = {

    s"stim queue STIM REQUEST ${logEntry(0)} STATE:\n" +
      s"stim request is ${if(logEntry(5) == 0) "INACTIVE" else "ACTIVE"}\n" +
      s"elec 0: ${BitDrawing.as32BinarySpaced(logEntry(1))}\n" +
      s"elec 1: ${BitDrawing.as32BinarySpaced(logEntry(2))}\n" +
      s"period: ${logEntry(3)}\n" +
      s"next firing: ${logEntry(4)}\n" +
      s"at timestep: ${logEntry(6)}\n"
  }

  def parseDACstate(logEntry: List[Int]): String = {

    s"stim queue DAC PAIR ${logEntry(0)} STATE:\n" +
      s"elec 0: ${BitDrawing.as32BinarySpaced(logEntry(1))}\n" +
      s"elec 1: ${BitDrawing.as32BinarySpaced(logEntry(2))}\n" +
      s"state: ${logEntry(3)}\n" +
      s"next state change: ${logEntry(4)}\n" +
      s"at timestep: ${logEntry(5)}\n"
  }

  val logHandlers = Map[Int, (List[Int] => String)](

    1  -> parseSQstates,
    2  -> parseSQbooking,
    3  -> parseSQstateChange,
    4  -> parseSQconfStart,
    6  -> parseSQconfReset,
    7  -> parseSQtriggerFired,

    10 -> parseInstructionReceived,

    11 -> parseSQcanary,

    12 -> unnamed,

    13 -> parseStimulus,

    14 -> parseSGstate,
    15 -> parseDACstate
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

    helper(log, List[String]()).reverse
  }


  def printLog(log: List[Int]): String = {
    val entries = readLog(log)
    s"DSP log with ${entries.size} entries:" + entries.mkString("\n", "\n------\n\n\n", "\n")
  }


  def printDspLog: IO[Unit] = for {
    entries <- getDspLog
    _       <- IO { say(printLog(entries.values)) }
  } yield ()
}
