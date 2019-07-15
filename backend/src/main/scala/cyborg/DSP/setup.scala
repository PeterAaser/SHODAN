package cyborg.dsp.calls
import cats.data.Kleisli
import cyborg._

import cyborg.twiddle.Reg
import fs2._
import cats._
import cats.syntax._
import cats.implicits._
import cats.effect.implicits._
import cats.effect._
import DspRegisters._
import scala.concurrent.duration._
import utilz._
import bonus._
import BitDrawing._
import Settings._

import cyborg.dsp.calls.DspCalls._

class DspSetup[F[_]: Sync](client: MEAMEHttpClient[F], calls: DspCalls[F]) {

  import client.DSP._
  import calls._


  /**
    * End point for requesting stimuli.
    * 
    * Maintains activation state of electrodes allowing electrodes to be toggled on and off.
    * Logic for how perturbations should be rendered should be handled elsewhere.
    * 
    * TODO: Why is this in setup??
    * Shouldn't it be in calls?
    */
  def stimuliRequestSink: Kleisli[Id,FullSettings,Pipe[F, StimReq, Unit]] = Kleisli{ conf =>
    def sink: Pipe[F, StimReq, Unit] = {
      def go(s: Stream[F, StimReq], electrodeActivatedState: Map[Int, Boolean]): Pull[F, Unit, Unit] = {
        s.pull.uncons1.flatMap {
          case Some((SetPeriod(idx, period), tl)) if !electrodeActivatedState(idx) =>
            Pull.eval(stimGroupChangePeriod(idx, period)) >>
            Pull.eval(enableStimReqGroup(idx)) >>
            go(tl, electrodeActivatedState.updated(idx, true))

          case Some((DisableStim(idx), tl)) if electrodeActivatedState(idx) =>
            Pull.eval(disableStimReqGroup(idx)) >>
            go(tl, electrodeActivatedState.updated(idx, false))

          case Some((SetPeriod(idx, period), tl)) =>
            Pull.eval(stimGroupChangePeriod(idx, period)) >>
            go(tl, electrodeActivatedState)

          case Some((_, tl)) => go(tl, electrodeActivatedState)

          case None => Pull.done
        }
      }

      ins => go(
        ins
          // .map{ x => say(x); x}
          .filter{ req => conf.dsp.allowed.contains(req.group)},

        conf.dsp.allowed.map((_, false)).toMap
      ).stream
    }

    sink: Id[Pipe[F,StimReq,Unit]]
  }


  /**
    Resets the device before configuring electrodes.
    Piecewise configuration is therefore not possible.

    1) Enables electrodes for stimuli
    2) Configures source DAC
    3) Sets electrode to auto mode
    4) Configures blanking

    Please don't ask questions about what the fuck auto mode does,
    you'll never be able to understand the brilliant 5D chess of the
    electro-engineers anyways.

    And yes, the difference between blanking and blanking protection escapes me
    */
  def configureElectrodes: Kleisli[F,FullSettings,Unit] = Kleisli(
    conf => {

      /**
        Toggles electrode mode to auto for all listed electrodes.
        An empty list signifies setting all electrodes to manual (0)
        */
      def setElectrodeModes(electrodes: List[Int]) = {
        val elec0 = electrodes.filter(idx => (idx >= 0  && idx < 15)).foldLeft(0){ case(acc, channel) => acc + (3 << ((channel -  0) * 2)) }
        val elec1 = electrodes.filter(idx => (idx >= 15 && idx < 30)).foldLeft(0){ case(acc, channel) => acc + (3 << ((channel - 15) * 2)) }
        val elec2 = electrodes.filter(idx => (idx >= 30 && idx < 45)).foldLeft(0){ case(acc, channel) => acc + (3 << ((channel - 30) * 2)) }
        val elec3 = electrodes.filter(idx => (idx >= 45 && idx < 60)).foldLeft(0){ case(acc, channel) => acc + (3 << ((channel - 45) * 2)) }

        dspCall(SET_ELECTRODE_GROUP_MODE,
                elec0 -> ELECTRODE_MODE_ARG1,
                elec1 -> ELECTRODE_MODE_ARG2,
                elec2 -> ELECTRODE_MODE_ARG3,
                elec3 -> ELECTRODE_MODE_ARG4)
      }

      def configureStimGroup(group: Int, electrodes: List[Int]) = {
        val elec0 = electrodes.filter(_ <= 30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
        val elec1 = electrodes.filterNot(_ <= 30).map(_-30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }
        for {
          _ <- dspCall(CONFIGURE_ELECTRODE_GROUP,
                       group -> STIM_QUEUE_GROUP,
                       elec0  -> STIM_QUEUE_ELEC0,
                       elec1  -> STIM_QUEUE_ELEC1).void
        } yield ()
      }


      /**
        Toggles blanking on supplied electrodes. If an empty list is passed
        this is equivalent to untoggling blanking for all electrodes.
        */
      def configureBlanking(electrodes: List[Int], blanking: Boolean, blankingProtection: Boolean): F[Unit] = {
        val elec0 = electrodes
          .filter(_ < 30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }

        val elec1 = electrodes
          .filterNot(_ < 30).map(_-30).foldLeft(0){ case(acc, channel) => acc + (1 << channel) }

        val blankingCall =
          if(blanking)
            dspCall(SET_BLANKING,
                    elec0 -> BLANKING_EN_ELECTRODES1,
                    elec1 -> BLANKING_EN_ELECTRODES2)
          else
            dspCall(SET_BLANKING,
                    elec0 -> 0,
                    elec1 -> 0)

        val blankingProtectionCall =
          if(blankingProtection)
            dspCall(SET_BLANKING_PROTECTION,
                    elec0 -> BLANK_PROT_EN_ELECTRODES1,
                    elec1 -> BLANK_PROT_EN_ELECTRODES2)
          else
            dspCall(SET_BLANKING_PROTECTION,
                    elec0 -> 0,
                    elec1 -> 0)

        for {
          _ <- blankingCall
          _ <- blankingProtectionCall
        } yield ()
      }


      for {
        _   <- configureBlanking(
          conf.dsp.stimulusElectrodes.flatten,
          conf.dsp.blanking,
          conf.dsp.blankingProtection
        )
        _   <- configureStimGroup(0, conf.dsp.stimulusElectrodes.lift(0).getOrElse(Nil))
        _   <- configureStimGroup(1, conf.dsp.stimulusElectrodes.lift(1).getOrElse(Nil))
        _   <- configureStimGroup(2, conf.dsp.stimulusElectrodes.lift(2).getOrElse(Nil))
        _   <- setElectrodeModes(conf.dsp.stimulusElectrodes.flatten)
        cfg <- calls.readElectrodeConfig
        _   <- Fsay[F](cfg)
      } yield ()
    })
    ////////////////////////////////////////////////////////////////////////////////


  def flash: F[(Boolean, Boolean)] = {
    val h = for {
      isFlashed <- client.DSP.flashDsp.map(_ => true).handleError(_ => false)
      isHealthy <- calls.getDspHealth.handleError(_ => false)
    } yield (isFlashed, isHealthy)

    h.handleError{_ => say("Warning, DSP flash failed", Console.RED); (false, false)}
  }


  def setup: Kleisli[F,FullSettings,Unit] = Kleisli(
    conf => {

      say("SETTING UP DSP")
      val flashAndResetDsp = for {
        _ <- client.DSP.flashDsp
        _ <- stopStimQueue
        _ <- resetStimQueue
      } yield ()

      val mVperUnit = 0.571
      val voltage = conf.perturbation.amplitude
      val rem = voltage % mVperUnit
      val percent = rem/voltage
      if(percent > 0.1){
        say("### SIGNIFICANT MISMATCH BETWEEN DESIRED AND SUPPLIED VOLTAGE ###", Console.RED)
        say("### YOUR REQUEST OF $voltage IS RENDERED " + f"${percent*100}%1.1f" + "% WRONG ###", Console.RED)
      }

      say(s"Uploading balanced square wave with amplitude: $voltage")
      val uploadWave = for {
        _ <- uploadSquareTest(100.millis, voltage)
      } yield ()

      val commitConfigAndStart = for {
        _ <- commitConfig
        _ <- startStimQueue
      } yield ()

      for {
        _ <- flashAndResetDsp
        _ <- uploadWave
        _ <- configureElectrodes(conf)
        _ <- commitConfigAndStart
      } yield ()
    })
}
