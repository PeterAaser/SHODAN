package cyborg

import org.scalajs.dom.html
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._
import org.scalajs.dom.raw.MouseEvent
import org.scalajs.dom

import org.scalajs.dom.document
import japgolly.scalajs.react._
import japgolly.scalajs.react.ReactDOM
import japgolly.scalajs.react.vdom.html_<^._

object tictac{

  case class Product(name: String, price: Double, category: String, stocked: Boolean)
  case class State(filterText: String, inStockOnly: Boolean)

  class Backend($: BackendScope[_, State])  {
    def onTextChange(e: ReactEventFromInput) =
      e.extract(_.target.value)(value =>
        $.modState(_.copy(filterText = value)))

    def onCheckBox(e: ReactEvent) =
      $.modState(s => s.copy(inStockOnly = !s.inStockOnly))
  }


  val ProductCategoryRow = ScalaComponent.builder[String]("ProductCategoryRow")
    .render_P(category => <.tr(<.th(^.colSpan := 2, category)))
    .build

  val ProductRow = ScalaComponent.builder[Product]("ProductRow")
    .render_P((product: Product) =>
      <.tr(
        <.td(<.span(^.color.red.unless(product.stocked), product.name)),
        <.td(product.price))
      )
    .build


  def productFilter(s: State)(p: Product): Boolean =
    p.name.contains(s.filterText) &&
      (!s.inStockOnly || p.stocked)

  val ProductTable = ScalaComponent.builder[(List[Product], State)]("ProductTable")
    .render_P { case (products, state) =>
      val rows = products.filter(productFilter(state))
        .groupBy(_.category).toList
        .flatMap{ case (cat, ps) =>
          ProductCategoryRow.withKey(cat)(cat) :: ps.map(p => ProductRow.withKey(p.name)(p))}

        <.table(
          <.thead(
            <.tr(
              <.th("Name"),
              <.th("Price"))),
          <.tbody(
            rows.toVdomArray))
    }
    .build

  val SearchBar = ScalaComponent.builder[(State, Backend)]("SearchBar")
    .render_P { case(state, backend) =>
      <.form(
        <.input.text(
          ^.placeholder := "Search Bar...",
          ^.value := state.filterText,
          ^.onChange ==> backend.onTextChange
        ),
        <.p(
          <.input.checkbox(
            ^.onClick ==> backend.onCheckBox),
          "Only show products in stock"))
    }
    .build


  val FilterableProductTable = ScalaComponent.builder[List[Product]]("FilterableProductTable")
    .initialState(State("", false))
    .backend(new Backend(_))
    .renderPS(($, p, s) =>
      <.div(
        SearchBar((s,$.backend)),
        ProductTable((p,s))
      )
    ).build

  val PRODUCTS = List(
    Product("FootBall", 49.99, "Sporting Goods", true),
    Product("Baseball", 9.99, "Sporting Goods", true),
    Product("basketball", 29.99, "Sporting Goods", false),
    Product("ipod touch", 99.99, "Electronics", true),
    Product("iphone 5", 499.99, "Electronics", true),
    Product("Nexus 7", 199.99, "Electronics", true))
}
