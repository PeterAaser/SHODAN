package cyborg


object DspRegisters {

  import twiddle._

  case class RegisterSetList(addresses: List[Int], values: List[Int]) {
    override def toString: String =
      (addresses zip values).map{ case(addr, value) => s"\nwrote value 0x${value.toHexString} to 0x${addr.toHexString}"}.mkString("\n")
  }
  case class RegisterReadList(addresses: List[Int])
  case class RegisterReadResponse(addresses: List[Int], values: List[Int]){
    def asMap: Map[Reg, Word] = (addresses zip values).map(x => (Reg(x._1),Word(x._2))).toMap
    override def toString: String = {
      (addresses zip values).map{ case(a, v) => s"at address 0x${a.toHexString}: ${v}" }.mkString("\n","\n","\n")
    }

    def apply(address: Int): Option[Word] = asMap.lift(Reg(address))
  }

  object RegisterSetList {
    def apply(r: List[(Int,Int)]): RegisterSetList = RegisterSetList(r.unzip._2, r.unzip._1)

    // value -> address
    def apply(r: (Int, Int)*): RegisterSetList = RegisterSetList(r.unzip._2.toList, r.unzip._1.toList)
  }

  object RegisterReadList {
    def apply(addresses: Int*): RegisterReadList = RegisterReadList(addresses.toList)
  }

  object RegisterReadResponse {
    def apply(tups: List[(Int, Int)]): RegisterReadResponse = RegisterReadResponse(tups.map(_._1), tups.map(_._2))
  }


  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  ///// HS1
  val HS1_BASE           = (0x8000)
  val BLANKING_EN1       = (HS1_BASE + 0x150)
  val BLANKING_EN2       = (HS1_BASE + 0x154)

  val BLANKING_PROT1     = (HS1_BASE + 0x140)
  val BLANKING_PROT2     = (HS1_BASE + 0x144)
  ////////////////////////////////////////
  ////////////////////////////////////////
  ////////////////////////////////////////
  ///// STG
  val MAIL_BASE          = (0x1000)
  val MAIL_BASE_END      = (0x1FFC)

  val SLAVE_INSTRUCTION_ID     = (0x1000)
  val MASTER_INSTRUCTION_ID     = (0x1004)

  val INSTRUCTION_TYPE   = (0x1008)

  val STIM_QUEUE_BASE    = (0x100c)
  val STIM_QUEUE_RUNNING = (STIM_QUEUE_BASE + 0x0)

  val STIM_QUEUE_GROUP   = (STIM_QUEUE_BASE + 0x4)
  val STIM_QUEUE_PERIOD  = (STIM_QUEUE_BASE + 0x8)
  val STIM_QUEUE_ELEC0   = (STIM_QUEUE_BASE + 0xc)
  val STIM_QUEUE_ELEC1   = (STIM_QUEUE_BASE + 0x10)

  val CFG_DEBUG_ELEC0 = STIM_QUEUE_BASE + 0x14
  val CFG_DEBUG_ELEC1 = STIM_QUEUE_BASE + 0x18
  val CFG_DEBUG_MODE0 = STIM_QUEUE_BASE + 0x1C
  val CFG_DEBUG_MODE1 = STIM_QUEUE_BASE + 0x20
  val CFG_DEBUG_MODE2 = STIM_QUEUE_BASE + 0x24
  val CFG_DEBUG_MODE3 = STIM_QUEUE_BASE + 0x28
  val CFG_DEBUG_DAC0  = STIM_QUEUE_BASE + 0x2c
  val CFG_DEBUG_DAC1  = STIM_QUEUE_BASE + 0x30
  val CFG_DEBUG_DAC2  = STIM_QUEUE_BASE + 0x34
  val CFG_DEBUG_DAC3  = STIM_QUEUE_BASE + 0x38

  val SHOTS_FIRED     = STIM_QUEUE_BASE + 0x3c

  val ERROR_FLAG      = STIM_QUEUE_BASE + 0x40
  val ERROR_START     = STIM_QUEUE_BASE + 0x44
  val ERROR_END       = STIM_QUEUE_BASE + 0x60
  val ERROR_ENTRIES   = STIM_QUEUE_BASE + 0x64

  val STEP_COUNTER    = STIM_QUEUE_BASE + 0x68

  val  DEBUG1 = (STIM_QUEUE_BASE + 0x90)
  val  DEBUG2 = (STIM_QUEUE_BASE + 0x94)
  val  DEBUG3 = (STIM_QUEUE_BASE + 0x98)
  val  DEBUG4 = (STIM_QUEUE_BASE + 0x9C)

  val STIM_REQ1_ACTIVE               = STIM_QUEUE_BASE + 0x6C
  val STIM_REQ1_PERIOD               = STIM_QUEUE_BASE + 0x70
  val STIM_REQ1_NEXT_FIRING_TIMESTEP = STIM_QUEUE_BASE + 0x74

  val STIM_REQ2_ACTIVE               = STIM_QUEUE_BASE + 0x78
  val STIM_REQ2_PERIOD               = STIM_QUEUE_BASE + 0x7C
  val STIM_REQ2_NEXT_FIRING_TIMESTEP = STIM_QUEUE_BASE + 0x80

  val STIM_REQ3_ACTIVE               = STIM_QUEUE_BASE + 0x84
  val STIM_REQ3_PERIOD               = STIM_QUEUE_BASE + 0x88
  val STIM_REQ3_NEXT_FIRING_TIMESTEP = STIM_QUEUE_BASE + 0x8C

  val BLANKING_EN_ELECTRODES1 = (STIM_QUEUE_BASE + 0xA0)
  val BLANKING_EN_ELECTRODES2 = (STIM_QUEUE_BASE + 0xA4)

  val BLANK_PROT_EN_ELECTRODES1 = (STIM_QUEUE_BASE + 0xA8)
  val BLANK_PROT_EN_ELECTRODES2 = (STIM_QUEUE_BASE + 0xAC)

  val ELECTRODE_MODE_ARG1 = (STIM_QUEUE_BASE + 0xB0)
  val ELECTRODE_MODE_ARG2 = (STIM_QUEUE_BASE + 0xB4)
  val ELECTRODE_MODE_ARG3 = (STIM_QUEUE_BASE + 0xB8)
  val ELECTRODE_MODE_ARG4 = (STIM_QUEUE_BASE + 0xBC)


  val LOG_START          = (0x1100)
  val LOG_END            = (0x1F00)
  val LOG_ENTRIES        = (0x1FF0)

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

}
