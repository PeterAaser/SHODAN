package cyborg.frontend.views

import cyborg.frontend.routing._

import io.udash._

object RootViewFactory extends StaticViewFactory[RootState.type](() => new RootView)

class RootView extends ContainerView {
  import scalatags.JsDom.all._

  override val getTemplate: Modifier =
    div(
      h1("I am the root window."),
      childViewContainer
    )
}

object ErrorViewFactory extends StaticViewFactory[ErrorState.type](() => new ErrorView)

class ErrorView extends ContainerView {
  import scalatags.JsDom.all._

  override val getTemplate: Modifier =
    div(
      h1("Something happened :("),
      childViewContainer
    )
}

object LandingPageViewFactory extends StaticViewFactory[LandingPageState.type](() => new LandingView)

class LandingView extends ContainerView {
  import scalatags.JsDom.all._

  override val getTemplate: Modifier =
    div(
      h1("I am the landing view."),
      childViewContainer
    )
}
