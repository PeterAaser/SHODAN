package cyborg
object DspRegisters {

  import twiddle._

  case class RegisterSetList(addresses: List[Int], values: List[Int])
  case class RegisterReadList(addresses: List[Int])
  case class RegisterReadResponse(addresses: List[Int], values: List[Int])

  case object RegisterSetList {
    def apply(r: List[(Int,Int)]): RegisterSetList = RegisterSetList(r.unzip._2, r.unzip._1)
  }


  val MAIL_BASE          = (0x1000)
  val WRITE_REQ_ID       = (MAIL_BASE + 0xc)
  val WRITE_ACK_ID       = (MAIL_BASE + 0x10)
  val WRITE_ADDRESS      = (MAIL_BASE + 0x14)
  val WRITE_VALUE        = (MAIL_BASE + 0x18)

  val READ_REQ_ID        = (MAIL_BASE + 0x1c)
  val READ_ACK_ID        = (MAIL_BASE + 0x20)
  val READ_ADDRESS       = (MAIL_BASE + 0x24)
  val READ_VALUE         = (MAIL_BASE + 0x28)

  val DEBUG1             = (MAIL_BASE + 0x2c)
  val DEBUG2             = (MAIL_BASE + 0x30)
  val DEBUG3             = (MAIL_BASE + 0x34)
  val DEBUG4             = (MAIL_BASE + 0x38)
  val DEBUG5             = (MAIL_BASE + 0x3c)
  val DEBUG6             = (MAIL_BASE + 0x40)
  val DEBUG7             = (MAIL_BASE + 0x44)
  val DEBUG8             = (MAIL_BASE + 0x48)
  val DEBUG9             = (MAIL_BASE + 0x4c)

  val WRITTEN_ADDRESS    = (MAIL_BASE + 0x50)
  val COUNTER            = (MAIL_BASE + 0x54)
  val PING_SEND          = (MAIL_BASE + 0x58)
  val PING_READ          = (MAIL_BASE + 0x5c)
  val CLEAR              = (MAIL_BASE + 0x60)

  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  ///// STG
  val STIM_BASE          = (0x9000)

  val ELECTRODE_ENABLE   = (STIM_BASE + 0x158)
  val ELECTRODE_ENABLE1  = (STIM_BASE + 0x158)
  val ELECTRODE_ENABLE2  = (STIM_BASE + 0x15c)

  val ELECTRODE_MODE     = (STIM_BASE + 0x120)
  val ELECTRODE_MODE1    = (STIM_BASE + 0x120)
  val ELECTRODE_MODE2    = (STIM_BASE + 0x124)
  val ELECTRODE_MODE3    = (STIM_BASE + 0x128)
  val ELECTRODE_MODE4    = (STIM_BASE + 0x12c)

  val ELECTRODE_DAC_SEL  = (STIM_BASE + 0x160)
  val ELECTRODE_DAC_SEL1 = (STIM_BASE + 0x160)
  val ELECTRODE_DAC_SEL2 = (STIM_BASE + 0x164)
  val ELECTRODE_DAC_SEL3 = (STIM_BASE + 0x168)
  val ELECTRODE_DAC_SEL4 = (STIM_BASE + 0x16c)

  val TRIGGER_REPEAT1    = (STIM_BASE + 0x190)
  val TRIGGER_REPEAT2    = (STIM_BASE + 0x194)
  val TRIGGER_REPEAT3    = (STIM_BASE + 0x198)


  val STG_MWRITE1  = (STIM_BASE + 0xf20)
  val STG_MWRITE2  = (STIM_BASE + 0xf24)
  val STG_MWRITE3  = (STIM_BASE + 0xf28)
  val STG_MWRITE4  = (STIM_BASE + 0xf2c)
  val STG_MWRITE5  = (STIM_BASE + 0xf30)
  val STG_MWRITE6  = (STIM_BASE + 0xf34)
  val STG_MWRITE7  = (STIM_BASE + 0xf38)
  val STG_MWRITE8  = (STIM_BASE + 0xf3c)

  val STG_MCLEAR_AND_WRITE1  = (STIM_BASE + 0xf40)
  val STG_MCLEAR_AND_WRITE2  = (STIM_BASE + 0xf44)
  val STG_MCLEAR_AND_WRITE3  = (STIM_BASE + 0xf48)
  val STG_MCLEAR_AND_WRITE4  = (STIM_BASE + 0xf4c)
  val STG_MCLEAR_AND_WRITE5  = (STIM_BASE + 0xf50)
  val STG_MCLEAR_AND_WRITE6  = (STIM_BASE + 0xf54)
  val STG_MCLEAR_AND_WRITE7  = (STIM_BASE + 0xf58)
  val STG_MCLEAR_AND_WRITE8  = (STIM_BASE + 0xf5c)


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  ///// TRIGGERS

  val  TRIGGER_CTRL_BASE  = (0x0200)

  val  TRIGGER_CTRL       = (TRIGGER_CTRL_BASE)
  val  TRIGGER_CTRL1      = (TRIGGER_CTRL_BASE)
  val  START_STIM1        = (TRIGGER_CTRL_BASE + 0x4)
  val  END_STIM1          = (TRIGGER_CTRL_BASE + 0x8)
  val  WRITE_START1       = (TRIGGER_CTRL_BASE + 0xc)
  val  READ_START1        = (TRIGGER_CTRL_BASE + 0x10)

  val  TRIGGER_CTRL2      = (TRIGGER_CTRL_BASE + 0x20)
  val  START_STIM2        = (TRIGGER_CTRL_BASE + 0x24)
  val  END_STIM2          = (TRIGGER_CTRL_BASE + 0x28)
  val  WRITE_START2       = (TRIGGER_CTRL_BASE + 0x2c)
  val  READ_START2        = (TRIGGER_CTRL_BASE + 0x30)

  val  TRIGGER_CTRL3      = (TRIGGER_CTRL_BASE + 0x40)
  val  START_STIM3        = (TRIGGER_CTRL_BASE + 0x44)
  val  END_STIM3          = (TRIGGER_CTRL_BASE + 0x48)
  val  WRITE_START3       = (TRIGGER_CTRL_BASE + 0x4c)
  val  READ_START3        = (TRIGGER_CTRL_BASE + 0x50)

  val  MANUAL_TRIGGER     = (TRIGGER_CTRL_BASE + 0x14)


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  ///// STG 1 (possible duplicates)


  val regMap = Map(
  "MAIL_BASE             " -> (0x1000),
  "WRITE_REQ_ID          " -> (MAIL_BASE + 0xc),
  "WRITE_ACK_ID          " -> (MAIL_BASE + 0x10),
  "WRITE_ADDRESS         " -> (MAIL_BASE + 0x14),
  "WRITE_VALUE           " -> (MAIL_BASE + 0x18),
  "READ_REQ_ID           " -> (MAIL_BASE + 0x1c),
  "READ_ACK_ID           " -> (MAIL_BASE + 0x20),
  "READ_ADDRESS          " -> (MAIL_BASE + 0x24),
  "READ_VALUE            " -> (MAIL_BASE + 0x28),
  "DEBUG1                " -> (MAIL_BASE + 0x2c),
  "DEBUG2                " -> (MAIL_BASE + 0x30),
  "DEBUG3                " -> (MAIL_BASE + 0x34),
  "DEBUG4                " -> (MAIL_BASE + 0x38),
  "DEBUG5                " -> (MAIL_BASE + 0x3c),
  "DEBUG6                " -> (MAIL_BASE + 0x40),
  "DEBUG7                " -> (MAIL_BASE + 0x44),
  "DEBUG8                " -> (MAIL_BASE + 0x48),
  "DEBUG9                " -> (MAIL_BASE + 0x4c),
  "WRITTEN_ADDRESS       " -> (MAIL_BASE + 0x50),
  "COUNTER               " -> (MAIL_BASE + 0x54),
  "PING_SEND             " -> (MAIL_BASE + 0x58),
  "PING_READ             " -> (MAIL_BASE + 0x5c),
  "CLEAR                 " -> (MAIL_BASE + 0x60),
  "STIM_BASE             " -> (0x9000),
  "ELECTRODE_ENABLE      " -> (STIM_BASE + 0x158),
  "ELECTRODE_ENABLE1     " -> (STIM_BASE + 0x158),
  "ELECTRODE_ENABLE2     " -> (STIM_BASE + 0x15c),
  "ELECTRODE_MODE        " -> (STIM_BASE + 0x120),
  "ELECTRODE_MODE1       " -> (STIM_BASE + 0x120),
  "ELECTRODE_MODE2       " -> (STIM_BASE + 0x124),
  "ELECTRODE_MODE3       " -> (STIM_BASE + 0x128),
  "ELECTRODE_MODE4       " -> (STIM_BASE + 0x12c),
  "ELECTRODE_DAC_SEL     " -> (STIM_BASE + 0x160),
  "ELECTRODE_DAC_SEL1    " -> (STIM_BASE + 0x160),
  "ELECTRODE_DAC_SEL2    " -> (STIM_BASE + 0x164),
  "ELECTRODE_DAC_SEL3    " -> (STIM_BASE + 0x168),
  "ELECTRODE_DAC_SEL4    " -> (STIM_BASE + 0x16c),
  "TRIGGER_REPEAT1       " -> (STIM_BASE + 0x190),
  "TRIGGER_REPEAT2       " -> (STIM_BASE + 0x194),
  "TRIGGER_REPEAT3       " -> (STIM_BASE + 0x198),
  "STG_MWRITE1           " -> (STIM_BASE + 0xf20),
  "STG_MWRITE2           " -> (STIM_BASE + 0xf24),
  "STG_MWRITE3           " -> (STIM_BASE + 0xf28),
  "STG_MWRITE4           " -> (STIM_BASE + 0xf2c),
  "STG_MWRITE5           " -> (STIM_BASE + 0xf30),
  "STG_MWRITE6           " -> (STIM_BASE + 0xf34),
  "STG_MWRITE7           " -> (STIM_BASE + 0xf38),
  "STG_MWRITE8           " -> (STIM_BASE + 0xf3c),
  "STG_MCLEAR_AND_WRITE1 " -> (STIM_BASE + 0xf40),
  "STG_MCLEAR_AND_WRITE2 " -> (STIM_BASE + 0xf44),
  "STG_MCLEAR_AND_WRITE3 " -> (STIM_BASE + 0xf48),
  "STG_MCLEAR_AND_WRITE4 " -> (STIM_BASE + 0xf4c),
  "STG_MCLEAR_AND_WRITE5 " -> (STIM_BASE + 0xf50),
  "STG_MCLEAR_AND_WRITE6 " -> (STIM_BASE + 0xf54),
  "STG_MCLEAR_AND_WRITE7 " -> (STIM_BASE + 0xf58),
  "STG_MCLEAR_AND_WRITE8 " -> (STIM_BASE + 0xf5c),
  "TRIGGER_CTRL_BASE     " -> (0x0200),
  "TRIGGER_CTRL          " -> (TRIGGER_CTRL_BASE),
  "TRIGGER_CTRL1         " -> (TRIGGER_CTRL_BASE),
  "START_STIM1           " -> (TRIGGER_CTRL_BASE + 0x4),
  "END_STIM1             " -> (TRIGGER_CTRL_BASE + 0x8),
  "WRITE_START1          " -> (TRIGGER_CTRL_BASE + 0xc),
  "READ_START1           " -> (TRIGGER_CTRL_BASE + 0x10),
  "TRIGGER_CTRL2         " -> (TRIGGER_CTRL_BASE + 0x20),
  "START_STIM2           " -> (TRIGGER_CTRL_BASE + 0x24),
  "END_STIM2             " -> (TRIGGER_CTRL_BASE + 0x28),
  "WRITE_START2          " -> (TRIGGER_CTRL_BASE + 0x2c),
  "READ_START2           " -> (TRIGGER_CTRL_BASE + 0x30),
  "TRIGGER_CTRL3         " -> (TRIGGER_CTRL_BASE + 0x40),
  "START_STIM3           " -> (TRIGGER_CTRL_BASE + 0x44),
  "END_STIM3             " -> (TRIGGER_CTRL_BASE + 0x48),
  "WRITE_START3          " -> (TRIGGER_CTRL_BASE + 0x4c),
  "READ_START3           " -> (TRIGGER_CTRL_BASE + 0x50),
  "MANUAL_TRIGGER        " -> (TRIGGER_CTRL_BASE + 0x14)
  ).map(_.swap)

}
