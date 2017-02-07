package com.cyborg.views

import io.udash._
import com.cyborg.DemoStylesState
import com.cyborg.styles.DemoStyles
import org.scalajs.dom.Element

import scala.language.postfixOps

case object DemoStylesViewPresenter extends DefaultViewPresenterFactory[DemoStylesState.type](() => new DemoStylesView)

class DemoStylesView extends View {
  import scalacss.Defaults._
  import scalacss.ScalatagsCss._
  import scalatags.JsDom._
  import scalatags.JsDom.all._

  private val content = div(
    LocalStyles.render[TypedTag[org.scalajs.dom.raw.HTMLStyleElement]],
    h2(
      "You can find this demo source code in: ",
      i("com.cyborg.views.DemoStylesView")
    ),
    h3("Example"),
    p(LocalStyles.redItalic)("Red italic text."),
    p(LocalStyles.obliqueOnHover)("Hover me!"),
    h3("Read more"),
    ul(
      li(
        a(DemoStyles.underlineLinkBlack)(href := "http://guide.udash.io/#/frontend/templates", target := "_blank")("Read more in Udash Guide.")
      ),
      li(
       a(DemoStyles.underlineLinkBlack)(href := "https://japgolly.github.io/scalacss/book/", target := "_blank")("Read more in ScalaCSS docs.")
     )
    )
  )

  override def getTemplate: Modifier = content

  override def renderChild(view: View): Unit = {}

  object LocalStyles extends StyleSheet.Inline {
    import dsl._

    val redItalic = style(
      fontStyle.italic,
      color.red
    )

    val obliqueOnHover = style(
      fontStyle.normal,

      &.hover(
        fontStyle.oblique
      )
    )
  }
}