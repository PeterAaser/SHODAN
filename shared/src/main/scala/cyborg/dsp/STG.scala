package cyborg
import DspRegisters._

object STG {

  import twiddle._

  import spire.syntax.literals.radix._

  val TriggerSelectMap = Some(Map(x2"00" -> "Trigger 1",
                                  x2"01" -> "Trigger 2",
                                  x2"10" -> "Trigger 3"))

  val TriggerSelect = List(
    BitField(0x9104, "Mem 7", 2, 25, TriggerSelectMap),
    BitField(0x9104, "Mem 5", 2, 17, TriggerSelectMap),
    BitField(0x9104, "Mem 3", 2,  9, TriggerSelectMap),
    BitField(0x9104, "Mem 1", 2,  1, TriggerSelectMap),

    BitField(0x9108, "Mem 8", 2, 25, TriggerSelectMap),
    BitField(0x9108, "Mem 6", 2, 17, TriggerSelectMap),
    BitField(0x9108, "Mem 4", 2,  9, TriggerSelectMap),
    BitField(0x9108, "Mem 2", 2,  1, TriggerSelectMap)
  )

  val TriggerSelectBF = BitFieldGroup(
    "Assignment of trigger to stimulus memory source.",
    TriggerSelect,
    """
Assigns a trigger to a stimulus block. up to 8 memory
blocks are supported in normal operation, and multiple
triggers can use the same memory
"""
  )


  val ElectrodeModeMap = Some(Map(x2"00" -> "Auto", x2"11" -> "Manual"))
  val ElectrodeMode1 = generateBitFields(0x9120, "electrod mode", 1, 29, 0, ElectrodeModeMap)
  val ElectrodeMode2 = generateBitFields(0x9124, "electrod mode", 1, 29, 0, ElectrodeModeMap, 15)

  val ElectrodeModeBF = BitFieldGroup(
    "Electrode control mode",
    List(ElectrodeMode1, ElectrodeMode2).flatten,
    """
The state of an electrode in manual mode will always be decided by
the electrode DAC assignment registers (0x9160 and 0x9164), and
the electrode enable registers.

An electrode in automatic mode is additionally controlled by the sideband.
This mode is described in docs/dsp.org
"""
  )


  val SidebandSelectMap = Some(Map(x2"00" -> "Sideband 1",
                                   x2"01" -> "Sideband 2",
                                   x2"10" -> "Sideband 3"))

  val SidebandSelect = List(
    BitField(0x9154, "DAC F", 2, 25, SidebandSelectMap),
    BitField(0x9154, "DAC D", 2, 21, SidebandSelectMap),
    BitField(0x9154, "DAC B", 2, 17, SidebandSelectMap),
    BitField(0x9154, "DAC E", 2,  9, SidebandSelectMap),
    BitField(0x9154, "DAC C", 2,  5, SidebandSelectMap),
    BitField(0x9154, "DAC A", 2,  1, SidebandSelectMap),
  )

  val SidebandSelectBF = BitFieldGroup(
    "Sideband select",
    SidebandSelect,
    """
Assigns DACs to sidebands. When in auto mode an electrode that is enabled in Electrode Enable
and has stimulus assigned in Electrode Enable and a DAC assigned in DAC select these signals
will only be valid when activated by the sideband.
"""
  )

  val ElectrodeEnable1 = generateBitFields(0x9158, "electrode enable", 1, 29, 0, None)
  val ElectrodeEnable2 = generateBitFields(0x915c, "electrode enable", 1, 29, 0, None, 15)

  val ElectrodEnableBF = BitFieldGroup(
    "Electrode enable",
    List(ElectrodeEnable1, ElectrodeEnable2).flatten,
    """
When enabled the electrode stimulation switch enable can still be logic low if the electrode
is in auto mode.
""")



  val DACSelectMap = Some(Map(x2"00" -> "GND",
                                        x2"01" -> "DAC A/B",
                                        x2"10" -> "DAC C/D",
                                        x2"11" -> "DAC E/F"))

  val DACSelect1 = generateBitFields(0x9160, "electrode", 1, 29, 0, DACSelectMap)
  val DACSelect2 = generateBitFields(0x9164, "electrode", 1, 29, 0, DACSelectMap, 15)

  val DACSelectBF = BitFieldGroup(
    "Electrode assignment",
    List(DACSelect1, DACSelect2).flatten,
    """Decides which DAC should be used as stimulus source. May be overriden to ground by the
sideband if electrode mode is set to auto.
""")



  val VoltageCurrentMap = Some(Map(x2"0" -> "Current", x2"1" -> "Voltage"))
  val VoltageSelect = List(
    BitField(0x9100, "DAC F", 1, 13, VoltageCurrentMap),
    BitField(0x9100, "DAC E", 1, 12, VoltageCurrentMap),
    BitField(0x9100, "DAC D", 1, 11, VoltageCurrentMap),
    BitField(0x9100, "DAC C", 1, 10, VoltageCurrentMap),
    BitField(0x9100, "DAC B", 1,  9, VoltageCurrentMap),
    BitField(0x9100, "DAC A", 1,  8, VoltageCurrentMap)
  )


  val VoltageSelectBF = BitFieldGroup(
    "Voltage/Current switch",
    VoltageSelect,
    "Decides which flavor of the ole' Mike Pence treatment the neurons will get"
  )


  // Something is weird with the decoding here. Why have 4 bits? Fucking mcs...
  val DataSourceSelectMap = Some(Map(x2"0000" -> "Block 1 data stream",
                                     x2"0001" -> "Block 2 data stream",
                                     x2"0010" -> "Block 3 data stream",
                                     x2"0011" -> "Block 4 data stream",
                                     x2"0100" -> "Block 5 data stream",
                                     x2"0101" -> "Block 6 data stream",
                                     x2"0110" -> "Block 7 data stream",
                                     x2"0111" -> "Block 8 data stream")
  )

  val DataSourceSelect = List(
    BitField(0x91D0, "DAC F", 4, 27, DataSourceSelectMap),
    BitField(0x91D0, "DAC D", 4, 23, DataSourceSelectMap),
    BitField(0x91D0, "DAC B", 4, 19, DataSourceSelectMap),
    BitField(0x91D0, "DAC E", 4, 11, DataSourceSelectMap),
    BitField(0x91D0, "DAC C", 4,  7, DataSourceSelectMap),
    BitField(0x91D0, "DAC A", 4,  3, DataSourceSelectMap)
  )

  val DataSourceSelectBF = BitFieldGroup(
    "DAC stimulus source block assignment",
    DataSourceSelect,
    """Assigns which block from the stimulus pattern memory the DAC should
be reading from.
"""
  )


  val SidebandSourceSelect = List(
    BitField(0x91D4, "SBS 3", 4, 11, DataSourceSelectMap),
    BitField(0x91D4, "SBS 2", 4,  7, DataSourceSelectMap),
    BitField(0x91D4, "SBS 1", 4,  3, DataSourceSelectMap)
  )

  val SidebandSourceSelectBF = BitFieldGroup(
    "Sideband data source block assignment",
    SidebandSelect,
    """Assigns which block from the stimulus pattern memory the sideband
should be reading from. For some reason stimulus and sideband data live
in the same memory-area.
"""
  )


  // According to regmap there is a gap here between E8 and F0
  val WeightFactor = List(
    BitField(0x91E0, "DAC A Weighting factor", 18, 17, None),
    BitField(0x91E4, "DAC C Weighting factor", 18, 17, None),
    BitField(0x91E8, "DAC E Weighting factor", 18, 17, None),
    BitField(0x91F0, "DAC B Weighting factor", 18, 17, None),
    BitField(0x91F4, "DAC D Weighting factor", 18, 17, None),
    BitField(0x91F8, "DAC F Weighting factor", 18, 17, None)
  )

  val WeightFactorBF = BitFieldGroup(
    "Weight factor",
    WeightFactor,
    "No description in the manual, guesswork as to what this does based on the name"
  )
}
