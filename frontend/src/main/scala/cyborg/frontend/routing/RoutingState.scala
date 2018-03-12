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
case object IndexState extends FinalRoutingState(Some(RootState))
case object LandingPageState extends FinalRoutingState(Some(RootState))
case object ErrorState extends FinalRoutingState(Some(RootState))

// import io.udash._

// sealed abstract class RoutingState(val parentState: Option[ContainerRoutingState]) extends State {
//   override type HierarchyRoot = RoutingState
// }

// sealed abstract class ContainerRoutingState(parentState: Option[ContainerRoutingState])
//   extends RoutingState(parentState) with ContainerState

// sealed abstract class FinalRoutingState(parentState: Option[ContainerRoutingState])
//   extends RoutingState(parentState) with FinalState

// case object RootState extends ContainerRoutingState(None)
// case object LoginPageState extends FinalRoutingState(Some(RootState))
// case object ChatState extends ContainerRoutingState(Some(RootState))
