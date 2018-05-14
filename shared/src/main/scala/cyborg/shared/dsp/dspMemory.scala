package cyborg

import twiddle._
import bonus._
import com.avsystem.commons.serialization.HasGenCodec
import DspRegisters._


/**
  Lacks serialization logic because this class should not contain "hot" information,
  thus it should not be transferred over wire.
  */
case class RegistryGroup(
  name: String,
  fields: List[Field],

  // for instance get(0) => Some("Trigger 1")
  valueToSettings: Map[Bits, SettingName],
  description: String){
  val settingsToValue:  Map[SettingName, Bits] = valueToSettings.map(_.swap)
  val fieldNameToField: Map[FieldName, Field]  = fields.sorted.map(λ => (λ.name, λ)).toMap
  val groupedByAddress: List[(Reg, List[Field])] = fields.sorted.groupBy(λ => λ.address).toList

  def getReadList: RegisterReadList =
    RegisterReadList(fields.map(_.address.r).toSet.toList)
}
