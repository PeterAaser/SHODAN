package cyborg

import cats.data.Chain
import cats.effect._
import cats._
import cats.effect.concurrent.Ref
import cats.syntax._
import cats.implicits._
import fs2._
import cats.effect.{ Async, Concurrent, Effect, Sync, Timer }
import fs2.concurrent.{ InspectableQueue, Queue, Signal }
import fs2.io.tcp.Socket
import java.net.InetSocketAddress
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.duration.FiniteDuration
import cyborg.bonus._
import scala.concurrent.duration._

import cyborg.dsp.calls.DspCalls._
import cyborg.DspRegisters._
import cyborg.MEAMEmessages._

import cyborg.HttpClient._
import cyborg.utilz._

object mockRegisterDSP {

  /**
    The register based DSP mock is intended as a configuration target

    Functions written using c_style_snake_case denotes that they shadow
    a C function written on the DSP
    */
  case class DSPstate(registers: Map[Int,Int], cfg: ElectrodeCfg){
    def decodeDspCall(call: DspFuncCall): DSPstate = {

      def nextPeriod = call.args.toMap.apply(STIM_QUEUE_PERIOD)

      call.func match {
        case  DUMP                            => this
        case  RESET                           => this
        case  CONFIGURE_ELECTRODE_GROUP       => this
        case  SET_ELECTRODE_GROUP_MODE        => this
        case  COMMIT_CONFIG                   => this
        case  START_STIM_QUEUE                => this
        case  STOP_STIM_QUEUE                 => this
        case  SET_ELECTRODE_GROUP_PERIOD      => this
        case  ENABLE_STIM_GROUP               => this
        case  DISABLE_STIM_GROUP              => this
        case  COMMIT_CONFIG_DEBUG             => this
        case  WRITE_SQ_DEBUG                  => this
        case  SET_BLANKING                    => this
        case  SET_BLANKING_PROTECTION         => this
      }
    }

    def handle_configure_electrode_group(call: DspFuncCall): DSPstate = {
      val group_idx = call.sqGroup
      val elec0 = call.sqElec0
      val elec1 = call.sqElec1

      cfg.enabled_electrodes(0) |= elec0
      cfg.enabled_electrodes(1) |= elec1

      val electrodes = Array[Int](elec0, elec1)

      // 00 = None, 01 = trigger 1 and so forth.
      val trigger_mux = group_idx + 1;

      // Assigns DAC trigger sources to group index for every enabled electrode
      for(ii <- 0 until 4){

        // 0, 0,  1, 1
        val electrode_index  = ii/2;

        // 0, 15, 0, 15
        val electrode_offset = (ii%2)*15;

        // 0, 1,  2, 3
        val dac_index = ii;

        for(kk <- 0 until 15){

          val dac_offset = kk*2;
          val electrode_enabled = get_bit32(electrodes(electrode_index), kk+electrode_offset);

          if(electrode_enabled){
            cfg.DAC_select(dac_index) = set_field(cfg.DAC_select(dac_index), dac_offset, 2, trigger_mux);
          }
        }
      }
      ???
        // Du var her, du skulle finne ut om case class med Array i seg og copying
    }
  }

  /**
    Datatypes used by the DSP logic, not held in register space.
    By using Array getting 1:1 parity with the DSP is easier
    */
  case class ElectrodeCfg(
    enabled_electrodes: Array[Int],
    DAC_select:         Array[Int],
    electrode_mode:     Array[Int]
  ){
  }


  def set_field(word: Int, first_bit: Int, field_size: Int, field_value: Int): Int = {
    val field = (field_value << first_bit);
    var mask = (1 << field_size) - 1;
    mask = (mask << first_bit);
    val masked = ~((~word) | mask);

    field | masked;
  }

  def get_bit32(bits: Int, index: Int): Boolean =
    (((bits) >> (index)) & 1) != 0;
}
