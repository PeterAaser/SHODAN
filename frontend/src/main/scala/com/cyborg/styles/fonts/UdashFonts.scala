package com.cyborg.styles.fonts
import scala.language.postfixOps
import scalacss.internal.AV
import scalacss.Defaults._

object UdashFonts extends StyleSheet.Inline {
  import dsl._

  def acumin(fontWeight: AV = FontWeight.Regular, fontStyle: AV = FontStyle.Normal) = style(
    fontFamily :=! FontFamily.Acumin,
    fontStyle,
    fontWeight
  )
}

object FontFamily {
  val Acumin = "'acumin-pro', san-serif"
}

object FontWeight extends StyleSheet.Inline {
  import dsl._
  val ExtraLight: AV = fontWeight._200
  val Light: AV = fontWeight._300
  val Regular: AV  = fontWeight._400
  val SemiBold: AV = fontWeight._600
  val Bold: AV = fontWeight._700
}

object FontStyle extends StyleSheet.Inline {
  import dsl._
  val Normal: AV = fontStyle.normal
  val Italic: AV = fontStyle.italic
}