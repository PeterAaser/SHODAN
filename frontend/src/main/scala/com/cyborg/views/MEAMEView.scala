package com.cyborg.views

import com.cyborg.rpc.NotificationsClient
import com.cyborg.rpc.VisualizerClient
import com.cyborg.rpc.MEAMEControlClient
import com.cyborg.wallAvoid.Agent
import io.udash._
import com.cyborg.MEAMEState

import org.scalajs.dom.Element
import scalatags.JsDom.tags2.main
import scalacss.ScalatagsCss._

import com.cyborg.styles.DemoStyles

import scala.util.{Success, Failure}

case object MEAMEPresenter extends DefaultViewPresenterFactory[MEAMEState.type](() =>
  {
    import com.cyborg.Context._

    serverRpc.ping(10) onComplete {
      case Success(response) => println(s"Pong($response)")
      case Failure(ex) => println(s"PongError($ex)")
    }

    NotificationsClient.registerListener((msg: String) => println(msg))

    new MEAMEView()
})

class MEAMEView extends View {

  import scalatags.JsDom.all._
  import com.cyborg.Context._
  import com.cyborg.Visualizer
  import org.scalajs.dom.html
  import org.scalajs.dom


  import org.scalajs.dom.document
  val cantvas: html.Canvas = document.createElement("canvas").asInstanceOf[html.Canvas]
  val renderer =
    cantvas.getContext("2d")
      .asInstanceOf[dom.CanvasRenderingContext2D]

  val canvasControl = new Visualizer.VisualizerControl(cantvas)

  VisualizerClient.registerListener((agent: Agent) => canvasControl.update(agent))
  MEAMEControlClient.startMEAME

  cantvas.width = 800
  cantvas.height = 800
  renderer.fillStyle = "#AA5500"
  renderer.fillRect(0, 0, 200, 200)


  private val content = div(
    h2(
      "You can find this demo source code in: ",
      i("com.cyborg.views.RPCDemoView")
    ),
    h3("Example"),
    h3("Read more"),
    a(DemoStyles.underlineLinkBlack)(href := "http://guide.udash.io/#/rpc", target := "_blank")("Read more in Udash Guide."),
    h4("testan"),
    cantvas
  )

  override def getTemplate: Modifier = content

  override def renderChild(view: View): Unit = {}
}
