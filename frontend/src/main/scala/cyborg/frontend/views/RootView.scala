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

// import cyborg.frontend.routing.RootState
// import cyborg.frontend.services.TranslationsService
// import cyborg.shared.css.GlobalStyles
// import cyborg.shared.i18n.Translations
// import io.udash._
// import io.udash.bootstrap.UdashBootstrap.ComponentId
// import io.udash.bootstrap.button.{ButtonStyle, UdashButton}
// import io.udash.bootstrap.{BootstrapStyles, UdashBootstrap}
// import io.udash.css.CssView
// import io.udash.i18n.Lang

// class RootViewFactory(translationsService: TranslationsService) extends StaticViewFactory[RootState.type](
//   () => new RootView(translationsService)
// )

// class RootView(translationsService: TranslationsService) extends ContainerView with CssView {
//   import scalatags.JsDom.all._

//   private def langChangeButton(lang: Lang): Modifier  = {
//     val btn = UdashButton(
//       buttonStyle = ButtonStyle.Link, componentId = ComponentId(s"lang-btn-${lang.lang}")
//     )(lang.lang.toUpperCase())

//     btn.listen {
//       case UdashButton.ButtonClickEvent(_, _) =>
//         translationsService.setLanguage(lang)
//     }

//     btn.render
//   }

//   // ContainerView contains default implementation of child view rendering
//   // It puts child view into `childViewContainer`
//   override def getTemplate: Modifier = div(
//     // loads Bootstrap and FontAwesome styles from CDN
//     UdashBootstrap.loadBootstrapStyles(),
//     UdashBootstrap.loadFontAwesome(),

//     BootstrapStyles.container,
//     div(
//       GlobalStyles.floatRight,
//       Translations.langs.map(v => langChangeButton(Lang(v)))
//     ),
//     h1("SHODAN"),
//     childViewContainer
//   )
// }
