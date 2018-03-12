package cyborg.frontend.routing

import cyborg.frontend.views._

import io.udash._

class StatesToViewFactoryDef extends ViewFactoryRegistry[RoutingState] {
  def matchStateToResolver(state: RoutingState): ViewFactory[_ <: RoutingState] =
    state match {
      case RootState => RootViewFactory
      case LandingPageState => LandingPageViewFactory
      case IndexState => IndexViewFactory
      case _ => ErrorViewFactory
    }
}


// import cyborg.frontend.ApplicationContext
// import cyborg.frontend.views.RootViewFactory
// import cyborg.frontend.views.chat.ChatViewFactory
// import cyborg.frontend.views.login.LoginPageViewFactory
// import io.udash._

// class StatesToViewFactoryDef extends ViewFactoryRegistry[RoutingState] {
//   def matchStateToResolver(state: RoutingState): ViewFactory[_ <: RoutingState] =
//     state match {
//       case RootState => new RootViewFactory(
//         ApplicationContext.translationsService
//       )
//       case LoginPageState => new LoginPageViewFactory(
//         ApplicationContext.userService, ApplicationContext.application, ApplicationContext.translationsService
//       )
//       case ChatState => new ChatViewFactory(
//         ApplicationContext.userService, ApplicationContext.translationsService, ApplicationContext.notificationsCenter
//       )
//     }
// }
