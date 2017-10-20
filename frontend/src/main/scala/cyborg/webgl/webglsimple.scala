package cyborg

import org.scalajs.dom._

object webgltest {

  class webgltestController(canvas: html.Canvas) {

    import raw.WebGLRenderingContext._
    canvas.width = 1200
    canvas.height = 1200

    var gl: raw.WebGLRenderingContext = canvas.getContext("webgl").asInstanceOf[raw.WebGLRenderingContext]

    def test1(): Unit = {
      gl.viewport(0, 0, 1200, 1200)
      gl.clearColor(1.0, 0.0, 0.0, 1.0)
      gl.clear(COLOR_BUFFER_BIT)

      val buffer = gl.createBuffer()
      gl.bindBuffer(ARRAY_BUFFER, buffer)

      val tempVertices: scalajs.js.Array[Float] = scalajs.js.Array[Float]()
      tempVertices.push(
        -1.0f, -1.0f,
        1.0f,  -1.0f,
        -1.0f, 1.0f,
        -1.0f, 1.0f,
        1.0f,  -1.0f,
        1.0f,  1.0f
      )
      val vertices = new scalajs.js.typedarray.Float32Array(tempVertices)

      gl.bufferData(
        ARRAY_BUFFER,
        vertices,
        STATIC_DRAW)


      val vShader = gl.createShader(VERTEX_SHADER)
      val vertText =
        """
        attribute vec2 position;
          void main()
            {
              gl_Position = vec4(position, 0, 1);
            }
        """
      gl.shaderSource(vShader, vertText)
      gl.compileShader(vShader)


      val fShader = gl.createShader(FRAGMENT_SHADER)
      val fragText = fragmentShaders.mandelbrot

      gl.shaderSource(fShader, fragText)
      gl.compileShader(fShader)
      val program = gl.createProgram()
      gl.attachShader(program, vShader)
      gl.attachShader(program, fShader)
      gl.linkProgram(program)
      gl.useProgram(program)

      val positionLocation = gl.getAttribLocation(program, "position")
      gl.enableVertexAttribArray(positionLocation)
      gl.vertexAttribPointer(positionLocation, 2, FLOAT, false, 0, 0)

      gl.drawArrays(TRIANGLES, 0, 6)

    }
  }
}
