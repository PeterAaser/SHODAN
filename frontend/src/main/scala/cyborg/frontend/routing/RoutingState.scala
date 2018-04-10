package cyborg.frontend.routing


import io.udash._
sealed abstract class RoutingState(
  val parentState: Option[ContainerRoutingState]
)  extends State {
  override type HierarchyRoot = RoutingState
  def url(implicit application: Application[RoutingState]): String = s"/#${application.matchState(this).value}"
}

sealed abstract class ContainerRoutingState(
  parentState: Option[ContainerRoutingState]
) extends RoutingState(parentState) with ContainerState

sealed abstract class FinalRoutingState(
  parentState: Option[ContainerRoutingState]
) extends RoutingState(parentState) with FinalState

case object RootState extends ContainerRoutingState(None)
case object LandingPageState extends FinalRoutingState(Some(RootState))
case object ErrorState extends FinalRoutingState(Some(RootState))

case object IndexState extends ContainerRoutingState(Some(RootState))
case object LiveState extends FinalRoutingState(Some(IndexState))
case object RecordingState extends FinalRoutingState(Some(IndexState))
case object DspTestState extends FinalRoutingState(Some(IndexState))
