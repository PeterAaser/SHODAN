package cyborg.frontend.routing

import cyborg.frontend.views._

import io.udash._

class StatesToViewFactoryDef extends ViewFactoryRegistry[RoutingState] {
  def matchStateToResolver(state: RoutingState): ViewFactory[_ <: RoutingState] =
    state match {
      case RootState => RootViewFactory
      case LandingPageState => LandingPageViewFactory

      case IndexState => IndexViewFactory
      case LiveState => LiveViewFactory
      case RecordingState => RecordingViewFactory
      case DspTestState => DspTestViewFactory
      case DspMemoryState => DspMemoryViewFactory

      case MEAstate(meaID) => MEAviewFactory(meaID)

      case _ => ErrorViewFactory
    }
}
