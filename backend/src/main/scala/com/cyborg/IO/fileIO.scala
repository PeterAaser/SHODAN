package com.cyborg

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._

import java.io.File

/**
  Currently not in use, but might be useful in order to parse MCS data
  */
object fileIO {

  def getListOfFiles(dir: String): List[File] =
    (new File(dir)).listFiles.filter(_.isFile).toList


  val fmt = DateTimeFormat.forPattern("dd.MM.yyyy, HH:mm:ss")
  def timeString = DateTime.now().toString(fmt)


  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  def sortFilesByDate(files: List[File]) =
    files.map(_.getName).map(DateTime.parse(_, fmt)).sorted


  def getNewestFilename: String =
    sortFilesByDate(getListOfFiles("/home/peter/MEAMEdata"))
      .head.toString(fmt)


}
