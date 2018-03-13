package cyborg.frontend.routing

import io.udash._
class RoutingRegistryDef extends RoutingRegistry[RoutingState] {
  def matchUrl(url: Url): RoutingState =
    url2State.applyOrElse(
      url.value.stripSuffix("/"),
      (x: String) => LandingPageState
    )
  def matchState(state: RoutingState): Url =
    Url(state2Url.apply(state))
  private val (url2State, state2Url) = bidirectional {
    case "/idx" => IndexState
    case "/idx/live" => LiveState
  }
}
