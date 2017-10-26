package cyborg

import org.scalajs.dom._

object webglSimple{

  class webgltestController(canvas: html.Canvas) {

    canvas.width = 1200
    canvas.height = 1200

    var gl: raw.WebGLRenderingContext = canvas.getContext("webgl").asInstanceOf[raw.WebGLRenderingContext]

    

  }
}
