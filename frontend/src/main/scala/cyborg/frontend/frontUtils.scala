package cyborg

import io.udash.properties.model.ModelProperty
import io.udash.properties.single.Property
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
    val huh = g.console.log("%c[%s]%c%s", "color: #fff600; background: #333333" + s"${fname}: ${sourcecode.Line()}" + "color: #e0e0e0; background: #333333" + s" ## $word" + " "*rightPad)
    ()
  }

  implicit class PropertyOps[A](p: Property[A]){
    def modify(f: A => A): Unit = {
      val a = p.get
      p.set(f(a))
    }
  }

  def getColor(colorIdx: Int, renderer: org.scalajs.dom.CanvasRenderingContext2D) = colorIdx match {
    case 0 => renderer.fillStyle = "yellow"
    case 1 => renderer.fillStyle = "orange"
    case 2 => renderer.fillStyle = "green"
    case 3 => renderer.fillStyle = "cyan"

    case 4 => renderer.fillStyle = "yellow"
    case 5 => renderer.fillStyle = "yellow"
    case 6 => renderer.fillStyle = "yellow"
  }
}
