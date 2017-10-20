
package cyborg

object glUtils {


}

object fragmentShaders {

  val circle = """
  precision highp float;
  void main()
  {
    float normalizedX = gl_FragCoord.x - 320.0;
    float normalizedY = gl_FragCoord.y - 240.0;
    if (sqrt(normalizedX * normalizedX + normalizedY * normalizedY) < 80.0)
    {
      gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
    } else {
      gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    }
  }
  """

  val mandelbrot = """
  #define NUM_STEPS   100000
  #define ZOOM_FACTOR 2.0
  #define X_OFFSET    0.5

  #ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
  #else
    precision mediump float;
  #endif
  precision mediump int;

  precision highp float;
  void main() {
    vec2 z;
    float x,y;
    int steps;
    float normalizedX = (gl_FragCoord.x - 320.0) / 640.0 * ZOOM_FACTOR *
      (640.0 / 480.0) - X_OFFSET;
    float normalizedY = (gl_FragCoord.y - 240.0) / 480.0 * ZOOM_FACTOR;

    z.x = normalizedX;
    z.y = normalizedY;

    for (int i=0;i<NUM_STEPS;i++) {

      steps = i;

      x = (z.x * z.x - z.y * z.y) + normalizedX;
      y = (z.y * z.x + z.x * z.y) + normalizedY;

      if((x * x + y * y) > 4.0) {
        break;
      }

      z.x = x;
      z.y = y;

    }

    if (steps == NUM_STEPS-1) {
      gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
    } else {
      gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    }
  }
  """

  val hello = """
  precision mediump float;
  void main() {
    gl_FragColor = vec4(1, 0, 0.5, 1); // return redish-purple
  }
"""

}

object vertexShaders {

  val hello = """
attribute vec4 a_position;
void main() {
}
"""

}
