package cyborg.frontend
import cyborg.frontilz._


// import io.udash.logging.CrossLogging
import io.udash.wrappers.jquery._
import org.scalajs.dom.Element
import scala.scalajs.js.annotation.JSExport


object JSLauncher {
  import cyborg.frontend.Context

  @JSExport
  def main(args: Array[String]): Unit = {
    val notUsed = jQ((jThis: Element) => {
      val appRoot = jQ("#application").get(0)
      if (appRoot.isEmpty) {
        say("OH FUG :DDD")
      } else {
        Context.applicationInstance.run(appRoot.get)
      }
    })
  }
}
