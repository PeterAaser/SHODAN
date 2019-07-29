package cyborg

import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import frontilz._

import cyborg.frontend.services.rpc.RPCService
import cyborg.RPCmessages.DrawCommand

class StimVizControl(canvas: Canvas) {

  canvas.width = 600
  canvas.height = 200

  val renderer = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  renderer.font = "24px Arial"

  renderer.textAlign = "left"
  renderer.textBaseline = "left"
  renderer.fillStyle = "black"

  val state = collection.mutable.Map[Int, StimReq]()

  def render(req: StimReq): String =
    req match {
      case DisableStim(group) => s"Stim group $group DISABLED"
      case SetPeriod(group, period) => {
        val freq = 1000.0/period.toMillis
        s"Stim group $group\tfreq: " + f"$freq%1.2fHz" + f"\tperiod: ${1.0/freq}%1.2fs"
      }
    }

  def pushData(req: StimReq): Unit = {

    state(req.group) = req
    renderer.clearRect(0, 0, canvas.width.toDouble, canvas.height.toDouble)
    renderer.fillText(render(req), 10, 80 + (req.group*40))
    state.foreach{ case(_, req) => renderer.fillText(render(req), 10, 80 + (req.group*40))}
    
  }
}
