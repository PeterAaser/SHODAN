package com.cyborg

import com.github.nscala_time.time.Imports._
import com.github.nscala_time.time.Implicits._
import fs2.concurrent

import fs2.util.Async
import java.io.File
import java.nio.file.Paths
import fs2.io.tcp._
import fs2._
import params._

import spray.json._
import fommil.sjs.FamilyFormats._


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
