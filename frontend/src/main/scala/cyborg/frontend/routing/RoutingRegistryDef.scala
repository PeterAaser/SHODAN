package cyborg.frontend.routing

import io.udash._
import scala.util.Try

class RoutingRegistryDef extends RoutingRegistry[RoutingState] {
  def matchUrl(url: Url): RoutingState =
    url2State.applyOrElse(
      url.value.stripSuffix("/"),
      (x: String) => LandingPageState
    )
  def matchState(state: RoutingState): Url =
    Url(state2Url.apply(state))

  private val url2State: PartialFunction[String, RoutingState] = {
    case "/idx" => IndexState
    case "/idx/live" => LiveState
    case "/idx/playback" => RecordingState
    case "/idx/live/dspTests" => DspTestState
    case "/idx/live/dspTests/mem" => DspMemoryState
    case "/idx/live/MEA" / arg => MEAstate(Try(arg.toInt).toOption)
  }

  private val state2Url: PartialFunction[RoutingState, String] = {
    case IndexState => "/idx"
    case LiveState => "/idx/live"
    case RecordingState => "/idx/playback"
    case DspTestState => "/idx/live/dspTests"
    case DspMemoryState => "/idx/live/dspTests/mem"
    case MEAstate(Some(id)) => s"/idx/live/MEA/$id"
    case MEAstate(None) => s"/idx/live/MEA/new"
  }
}
