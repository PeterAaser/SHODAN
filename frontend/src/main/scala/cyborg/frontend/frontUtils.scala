package cyborg

import org.scalajs.dom.raw.Console
import scala.language.higherKinds
import scala.reflect.ClassTag
import sourcecode._
import scala.scalajs.js.Dynamic.{ global => g }

import scalajs._
import scalajs.js._

object frontilz {

  def say(word: Any)(implicit filename: sourcecode.File, line: sourcecode.Line): Unit = {
    val fname = filename.value.split("/").last
    val sl = fname.length() + word.toString().length()
    val rightPad = 80 - sl
    g.console.log("%c[%s]%c%s", "color: #fff600; background: #333333", s"${fname}: ${sourcecode.Line()}", "color: #e0e0e0; background: #333333", s" - $word" + " "*rightPad)
  }

}
