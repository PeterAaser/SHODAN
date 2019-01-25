package cyborg.frontend

import cyborg.frontend.routing._
import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.utils.Icons
import scala.util.{ Failure, Success }


import cyborg._
import cyborg.RPCmessages._
import frontilz._

import io.udash.css._


import org.scalajs.dom.document
import scalatags.JsDom.all._
import org.scalajs.dom.html
import io.udash.bootstrap.button._

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalajs.dom.document
import org.scalajs.dom.html

object compz {

  def renderDBrecord(r: Property[RecordingInfo]) = {
    produce(r)(r => ul(
      li(p( s"Date: ${r.date}" )),
      li(r.duration.map( x => p(s"Duration: $x")).getOrElse(p("Duration missing!"))),
      li(p( s"samplerate: ${r.daqSettings.samplerate}" )),
      li(p( s"segment length ${r.daqSettings.segmentLength}" )),
      li(p( s"comment: ${r.comment }" )),
    ).render)
  }

  def renderDBrecordSmall(rp: Property[RecordingInfo]): org.scalajs.dom.html.Paragraph = {
    def renderDuration(d: Option[String]) = d.getOrElse("UNKNOWN")
    val r = rp.get
    p(s"recording date: ${r.date}, duration: ${renderDuration(r.duration)}").render
  }

}
