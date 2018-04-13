package cyborg.frontend

// import io.udash.logging.CrossLogging
import io.udash.wrappers.jquery._
import org.scalajs.dom.Element
import scala.scalajs.js.annotation.JSExport


object JSLauncher {
  import cyborg.frontend.Context

  @JSExport
  def main(args: Array[String]): Unit = {
    jQ((jThis: Element) => {
      // Select #application element from index.html as root of whole app
      val appRoot = jQ("#application").get(0)
      if (appRoot.isEmpty) {
        // logger.error("Application root element not found! Check your index.html file!")
      } else {
        Context.applicationInstance.run(appRoot.get)
      }
    })
  }
}
