// package cyborg

// import Settings._
// import RPCmessages._
// import cats.effect.IO
// import fs2._
// import fs2.concurrent.SignallingRef

// object StateManager {

//   def getDspStatus: DSPstate = ???
//   def flashDsp: Unit = ???


//   def isFucked(p: ProgramState): Option[String] = {
//     if(!p.meame.alive)                              Some("MEAME is offline!")
//     else if(p.dsp.isFlashed && !p.dsp.dspResponds)  Some("DSP is fucked!")
//     else if(!p.dsp.isFlashed)                       Some("DSP not flashed!")
//     else if(!p.dataSource.isDefined && p.isRunning) Some("Data source not defined!")
//     else None
//   }


//   def unfuckulate(p: ProgramState): (Option[String], ProgramState) = {
//     val fucked = isFucked(p)
//     if(fucked.isDefined)
//       (fucked, p.copy(isRunning = false))
//     else
//       (None, p)
//   }
// }
