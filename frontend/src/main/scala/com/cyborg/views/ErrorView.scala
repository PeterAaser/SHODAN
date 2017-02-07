package com.cyborg.views

import io.udash._
import com.cyborg.IndexState
import org.scalajs.dom.Element

object ErrorViewPresenter extends DefaultViewPresenterFactory[IndexState.type](() => new ErrorView)

class ErrorView extends View {
  import scalatags.JsDom.all._

  private val content = h3(
    "URL not found!"
  )

  override def getTemplate: Modifier = content

  override def renderChild(view: View): Unit = {}
}