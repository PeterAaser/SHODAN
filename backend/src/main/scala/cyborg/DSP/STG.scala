package cyborg
import DspRoutines._

object STG {

  import twiddle._

  import spire.syntax.literals.radix._

  val invalidSetting = SettingName("Invalid")

  val TriggerSelectMap = Map(Bits(x2"00") -> SettingName("Trigger 1"),
                             Bits(x2"01") -> SettingName("Trigger 2"),
                             Bits(x2"10") -> SettingName("Trigger 3"))

  val TriggerSelect = List(
    Field(Reg(0x9104), 25, 2, FieldName("Mem 7"), (_,λ) => TriggerSelectMap.get(λ).getOrElse(invalidSetting).n),
    Field(Reg(0x9104), 17, 2, FieldName("Mem 5"), (_,λ) => TriggerSelectMap.get(λ).getOrElse(invalidSetting).n),
    Field(Reg(0x9104), 9,  2, FieldName("Mem 3"), (_,λ) => TriggerSelectMap.get(λ).getOrElse(invalidSetting).n),
    Field(Reg(0x9104), 1,  2, FieldName("Mem 1"), (_,λ) => TriggerSelectMap.get(λ).getOrElse(invalidSetting).n),

    Field(Reg(0x9108), 25, 2, FieldName("Mem 8"), (_,λ) => TriggerSelectMap.get(λ).getOrElse(invalidSetting).n),
    Field(Reg(0x9108), 17, 2, FieldName("Mem 6"), (_,λ) => TriggerSelectMap.get(λ).getOrElse(invalidSetting).n),
    Field(Reg(0x9108), 9,  2, FieldName("Mem 4"), (_,λ) => TriggerSelectMap.get(λ).getOrElse(invalidSetting).n),
    Field(Reg(0x9108), 1,  2, FieldName("Mem 2"), (_,λ) => TriggerSelectMap.get(λ).getOrElse(invalidSetting).n)
  )
  val TriggerSelectBF = RegistryGroup(
    "Assignment of trigger to stimulus memory source.",
    TriggerSelect,
    TriggerSelectMap,
    """
Assigns a trigger to a stimulus block. up to 8 memory
blocks are supported in normal operation, and multiple
triggers can use the same memory
"""
  )


  val ElectrodeModeMap = Map(
    Bits(x2"00") -> SettingName("Auto"),
    Bits(x2"11") -> SettingName("Manual"))

  def ElectrodeRender(f: Field, b: Bits): String = ElectrodeModeMap.get(b).getOrElse(invalidSetting).n

  val ElectrodeMode1 = Field.generateFields(Reg(0x9120), 0, 2, 2, 15, "electrod mode", ElectrodeRender)
  val ElectrodeMode2 = Field.generateFields(Reg(0x9124), 0, 2, 2, 15, "electrod mode", ElectrodeRender, 15)

  val ElectrodeModeBF = RegistryGroup(
    "Electrode control mode",
    List(ElectrodeMode1, ElectrodeMode2).flatten,
    ElectrodeModeMap,
    """
The state of an electrode in manual mode will always be decided by
the electrode DAC assignment registers (0x9160 and 0x9164), and
the electrode enable registers.

An electrode in automatic mode is additionally controlled by the sideband.
This mode is described in docs/dsp.org
"""
  )


  val SidebandSelectMap = Map(Bits(x2"00") -> SettingName("Sideband 1"),
                              Bits(x2"01") -> SettingName("Sideband 2"),
                              Bits(x2"10") -> SettingName("Sideband 3"))

  def SidebandRender(f: Field, b: Bits): String = SidebandSelectMap.get(b).getOrElse(invalidSetting).n

  val SidebandSelect = List(
    Field(Reg(0x9154),25,2, FieldName("DAC F"), SidebandRender),
    Field(Reg(0x9154),21,2, FieldName("DAC D"), SidebandRender),
    Field(Reg(0x9154),17,2, FieldName("DAC B"), SidebandRender),
    Field(Reg(0x9154),9 ,2, FieldName("DAC E"), SidebandRender),
    Field(Reg(0x9154),5 ,2, FieldName("DAC C"), SidebandRender),
    Field(Reg(0x9154),1 ,2, FieldName("DAC A"), SidebandRender),
  )

  val SidebandSelectBF = RegistryGroup(
    "Sideband select",
    SidebandSelect,
    SidebandSelectMap,
    """
Assigns DACs to sidebands. When in auto mode an electrode that is enabled in Electrode Enable
and has stimulus assigned in Electrode Enable and a DAC assigned in DAC select these signals
will only be valid when activated by the sideband.
"""
  )


  val ElectrodeEnableMap = Map(Bits(x2"0") -> SettingName("OFF"),
                               Bits(x2"1") -> SettingName("ON"))

  def ElectrodeEnableRender(f: Field, b: Bits): String = if(b.b == 0) "OFF" else "ON"

  val ElectrodeEnable1 = Field.generateFields(Reg(0x9158), 0, 1, 1, 30, "electrod mode", ElectrodeEnableRender)
  val ElectrodeEnable2 = Field.generateFields(Reg(0x915c), 0, 1, 1, 30, "electrod mode", ElectrodeEnableRender, 30)

  val ElectrodEnableBF = RegistryGroup(
    "Electrode enable",
    ElectrodeEnable1 ++ ElectrodeEnable2,
    ElectrodeEnableMap,
    """
When enabled the electrode stimulation switch enable can still be logic low if the electrode
is in auto mode.
""")



  val DACSelectMap = Map(Bits(x2"00") -> SettingName("GND"),
                         Bits(x2"01") -> SettingName("DAC A/B"),
                         Bits(x2"10") -> SettingName("DAC C/D"),
                         Bits(x2"11") -> SettingName("DAC E/F"))

  def DACSelectRender(f: Field, b: Bits): String = DACSelectMap.get(b).get.n


  val DACSelect1 = Field.generateFields(Reg(0x9160), 0, 1, 1, 30, "electrod", DACSelectRender)
  val DACSelect2 = Field.generateFields(Reg(0x9164), 0, 1, 1, 30, "electrod", DACSelectRender, 30)

  val DACSelectBF = RegistryGroup(
    "Electrode assignment",
    (DACSelect1 ++ DACSelect2),
    DACSelectMap,
    """Decides which DAC should be used as stimulus source. May be overriden to ground by the
sideband if electrode mode is set to auto.
""")



  val VoltageCurrentMap = Map(Bits(x2"0") -> SettingName("Current"),
                              Bits(x2"1") -> SettingName("Voltage"))

  def VoltageCurrentRender(f: Field, b: Bits): String = VoltageCurrentMap.get(b).get.n

  val VoltageSelect = List(
    Field(Reg(0x9100), 13, 1, FieldName("DAC F"), VoltageCurrentRender),
    Field(Reg(0x9100), 12, 1, FieldName("DAC E"), VoltageCurrentRender),
    Field(Reg(0x9100), 11, 1, FieldName("DAC D"), VoltageCurrentRender),
    Field(Reg(0x9100), 10, 1, FieldName("DAC C"), VoltageCurrentRender),
    Field(Reg(0x9100), 9,  1, FieldName("DAC B"), VoltageCurrentRender),
    Field(Reg(0x9100), 8,  1, FieldName("DAC A"), VoltageCurrentRender)
  )


  val VoltageSelectBF = RegistryGroup(
    "Voltage/Current switch",
    VoltageSelect,
    VoltageCurrentMap,
    "Decides which flavor of the ole' Mike Pence treatment the neurons will get"
  )


  // Something is weird with the decoding here. Why have 4 bits? Fucking mcs...
  val DataSourceSelectMap = Map(Bits(x2"0000") -> SettingName("Block 1 data stream"),
                                Bits(x2"0001") -> SettingName("Block 2 data stream"),
                                Bits(x2"0010") -> SettingName("Block 3 data stream"),
                                Bits(x2"0011") -> SettingName("Block 4 data stream"),
                                Bits(x2"0100") -> SettingName("Block 5 data stream"),
                                Bits(x2"0101") -> SettingName("Block 6 data stream"),
                                Bits(x2"0110") -> SettingName("Block 7 data stream"),
                                Bits(x2"0111") -> SettingName("Block 8 data stream"))

  def DataSourceSelectRender(f: Field, b: Bits): String = DataSourceSelectMap.get(b).getOrElse(invalidSetting).n

  val DataSourceSelect = List(
    Field(Reg(0x91D0), 27, 4, FieldName("DAC F"), DataSourceSelectRender),
    Field(Reg(0x91D0), 23, 4, FieldName("DAC D"), DataSourceSelectRender),
    Field(Reg(0x91D0), 19, 4, FieldName("DAC B"), DataSourceSelectRender),
    Field(Reg(0x91D0), 11, 4, FieldName("DAC E"), DataSourceSelectRender),
    Field(Reg(0x91D0),  7, 4, FieldName("DAC C"), DataSourceSelectRender),
    Field(Reg(0x91D0),  3, 4, FieldName("DAC A"), DataSourceSelectRender)
  )

  val DataSourceSelectBF = RegistryGroup(
    "DAC stimulus source block assignment",
    DataSourceSelect,
    DataSourceSelectMap,
    """Assigns which block from the stimulus pattern memory the DAC should
be reading from.
"""
  )


  val SidebandSourceSelect = List(
    Field(Reg(0x91D4),11 ,4,FieldName("SBS 3"), DataSourceSelectRender),
    Field(Reg(0x91D4), 7 ,4,FieldName("SBS 2"), DataSourceSelectRender),
    Field(Reg(0x91D4), 3 ,4,FieldName("SBS 1"), DataSourceSelectRender))

   val SidebandSourceSelectBF = RegistryGroup(
     "Sideband data source block assignment",
     SidebandSelect,
     DataSourceSelectMap,
     """Assigns which block from the stimulus pattern memory the sideband
 should be reading from. For some reason stimulus and sideband data live
 in the same memory-area.
 """
   )


//   // According to regmap there is a gap here between E8 and F0
//   val WeightFactor = Set(
//     BitField(0x91E0, "DAC A Weighting factor", 18, 0),
//     BitField(0x91E4, "DAC C Weighting factor", 18, 0),
//     BitField(0x91E8, "DAC E Weighting factor", 18, 0),
//     BitField(0x91F0, "DAC B Weighting factor", 18, 0),
//     BitField(0x91F4, "DAC D Weighting factor", 18, 0),
//     BitField(0x91F8, "DAC F Weighting factor", 18, 0)
//   )

//   val WeightFactorBF = ValueFields(
//     "Weight factor",
//     WeightFactor,
//     """No description in the manual, guesswork as to what this
// does based on the name
// """
//   )


   val StimMemControlMap = Map(Bits(x2"00") -> SettingName("not sure if legal."),
                               Bits(x2"01") -> SettingName("STG memory initialize to single segment"),
                               Bits(x2"10") -> SettingName("STG memory initialized to 256 segments"),
                               Bits(x2"11") -> SettingName("not sure if legal."))

  def StimMemControlRender(f: Field, b: Bits): String = StimMemControlMap.get(b).get.n

   val StimMemControl = List(
     Field(Reg(0x9200), 29, 2, FieldName("Stim 1 ctrl"), StimMemControlRender)
   )

   val StimMemControlBF = RegistryGroup(
     "Stimulus segmentation control",
     StimMemControl,
     StimMemControlMap,
     """A very confusingly documented register. In the documentation the two bits are
 separate fields, labeled as
 Init Ptr All   (bit 29)
 Init Ptr Seg.0 (bit 28)
 With the similarily confusing description:
 Initialization: Poll Bit after writing a '1' until it is '0' to wait on end of request! (????)

 Currently setting this to 01 should be sufficient to avoid any serious brain damage.
 """)


}
