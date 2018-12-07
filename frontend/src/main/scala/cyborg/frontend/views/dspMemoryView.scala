package cyborg.frontend.views

import cyborg.frontend.routing._

import io.udash._
import io.udash.bootstrap.UdashBootstrap
import io.udash.bootstrap.form.{ UdashForm, UdashInputGroup }
import io.udash.bootstrap.navs.UdashNavbar
import io.udash.bootstrap.table.UdashTable
import io.udash.bootstrap.utils.Icons
import org.scalajs.dom.html
import scala.concurrent.Await
import scala.util.{ Failure, Success }

import cyborg._
import frontilz._

import io.udash.css._

import org.scalajs.dom
import org.scalajs.dom.html.Input
import scalatags.JsDom.all._
import io.udash.bootstrap.button._
import org.scalajs.dom
import scalatags.JsDom
import JsDom.all._

import scala.language.postfixOps
import io.udash.css._

import org.scalajs.dom.{window, File}
import rx._

import scala.concurrent.ExecutionContext.Implicits.global

import cyborg.twiddle._

/**
  May this code never be a lasting part of the frontend. PLEASE NO
  */
class DspMemoryView(model: ModelProperty[DspMemoryModel], presenter: DspMemoryViewPresenter) extends ContainerView with CssView {

  val regGroups = STG.theWholeFuckingLot


  // UdashListGroup() ???
  def renderr(group: RegistryGroup) = {
    val units = group.groupedByAddress.map{ case(_, fields) =>
      val rows = fields.map{ f =>
        produce(model.subProp(_.knownValues)){p =>
          tr(
            td(i(s"${f.first}\t${f.last}\t").render),
            td(i(s"${}").render),
            td(i(f.ezRender(p)).render)
          ).render
        }
      }
      rows
    }
    units
  }

  // Bits, Name, Opt[Value]
  type TableEntry = (String, String, Option[String])

  // Address, Name, Fields
  type RegTableEntry = (Int, String, SeqProperty[TableEntry])

  val regs: SeqProperty[RegTableEntry] = SeqProperty.empty


  def addFieldsWithWord(s: SeqProperty[TableEntry], fs: List[Field], w: Word): Unit = {
    fs.foreach{f =>
      {
        s.append((s"${f.first} - ${f.last}",
                  s"${f.name.n}",
                  Some(f.getFieldString(w))))
      }
    }
  }


  def addRegisterGroupsWithWords(s: SeqProperty[RegTableEntry], group: RegistryGroup, regs: Map[Reg,Word]): Unit = {
    def addRegisterGroup(address: Int, name: String, fields: List[Field], word: Word): Unit = {
      val mpty: SeqProperty[TableEntry] = SeqProperty.empty
      addFieldsWithWord(mpty, fields, word)
      val entry = (
        address,
        name,
        mpty
      )
      s.append(entry)
    }

    bonus.intersect(
      group.fields.groupBy(_.address),
      regs
    ).toList.foreach{
      case(reg, z) => addRegisterGroup(reg.r, group.name, z._1, z._2)
    }
  }


  def addFields(s: SeqProperty[TableEntry], fs: List[Field]): Unit = {
    fs.foreach{f =>
      {
        s.append((s"${f.first} - ${f.last}",
                  s"${f.name.n}",
                  None))
      }
    }
  }

  def addRegisterGroups(s: SeqProperty[RegTableEntry], group: RegistryGroup): Unit = {

    // Add outer entry
    def addRegisterGroup(address: Int, name: String, fields: List[Field]): Unit = {
      val mpty: SeqProperty[TableEntry] = SeqProperty.empty

      // populate inner entry
      addFields(mpty, fields)

      // populate outer entry
      val entry = (
        address,
        name,
        // append inner entry
        mpty
      )

      // append outer entry
      s.append(entry)
    }

    group.fields.groupBy(_.address).toList.foreach {
      case(reg, fields) => {
        addRegisterGroup(reg.r, group.name, fields)
      }
    }
  }

  addRegisterGroups(regs, STG.SidebandSelectBF)

  def renderAsTable(s: SeqProperty[TableEntry]) = {
    UdashTable()(s)(
      headerFactory = Some(() => tr(th(b("Bits")), th(b("Name")), th(b("Value"))).render),
      rowFactory = (el) => tr(
        td(produce(el)(v => i(v._1).render)),
        td(produce(el)(v => i(v._2).render)),
        td(produce(el){v =>
             val vs = v._3.getOrElse("Unknown")
             i(vs).render
        })
      ).render
    )
  }


  val theButton = UdashButton()("Perform read op")
  theButton.listen {
    case UdashButton.ButtonClickEvent(btn, _) => {

      import cyborg.frontend.Context
      // val contents = Context.serverRpc.readDspMemory(STG.SidebandSelectBF.getReadList)
      // contents.onComplete {
      //   case Success(r) => {
      //     regs.clear()
      //     addRegisterGroupsWithWords(regs, STG.SidebandSelectBF, r.asMap)
      //   }

      //   case Failure(ex) => {
      //     say("FAILURE! EGADS!")
      //   }
      // }
    }
  }

  override def getTemplate: Modifier = {
    div(
      theButton.render,
      ul(
        repeat(regs){r =>
          div(
            li(
              s"${r.get._2} at 0x${r.get._1.toHexString}"
            ).render,
            ul(
              renderAsTable(r.get._3).render
            )
          ).render
        }
      )
    )
  }


}


class DspMemoryViewPresenter(model: ModelProperty[DspMemoryModel]) extends Presenter[DspMemoryState.type] {
  import cyborg.frontend.Context

  override def handleState(state: DspMemoryState.type): Unit = {}
}


case object DspMemoryViewFactory extends ViewFactory[DspMemoryState.type] {

  import scala.concurrent.duration._
  import cyborg.frontend.Context
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[DspMemoryState.type]) = {
    val model = ModelProperty( DspMemoryModel(0, 0, Map[Reg,Word]() ) )
    val presenter = new DspMemoryViewPresenter(model)
    val view = new DspMemoryView(model, presenter)
    (view, presenter)
  }
}

case class DspMemoryModel(base: Int, address: Int, knownValues: Map[Reg,Word])

object DspMemoryModel extends HasModelPropertyCreator[DspMemoryModel]
