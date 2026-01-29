/***************************************************************************************
* Copyright (c) 2021-2026 Beijing Institute of Open Source Chip (BOSC)
* Copyright (c) 2020-2026 Institute of Computing Technology, Chinese Academy of Sciences (ICT，CAS)
* 
* YuQuan is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*   
* See the Mulan PSL v2 for more details.
***************************************************************************************/
package OpenMc

import chisel3._
import chisel3.experimental.FlatIO
import chisel3.util._
import BUNDLE_PARAM._

class dfi_en_offset(width: Int) extends Module {
  val io = IO(new Bundle {
    val vld = Flipped(Bool())
    val vld_ph0 = Flipped(Bool())
    val vld_ph1 = Flipped(Bool())
    val latency = Flipped(UInt(BUNDLE_PARAM.McParamWidth.W))
    val offset_out = Output(UInt(width.W))
    val dfi_parameter_mode = Flipped(Bool())
  })

    val vld_delay    = VecInit(Seq.fill(3)(false.B))
        vld_delay(0) := RegNext(io.vld)
        vld_delay(1) := RegNext(vld_delay(0))
        vld_delay(2) := RegNext(vld_delay(1))
  val offset0  = WireInit(false.B)
  val offset1  = WireInit(false.B)
  // Define shift registers
  val shif_bit0 = RegInit(VecInit(Seq.fill(3)(0.U(1.W))))
  val shif_bit1 = RegInit(VecInit(Seq.fill(3)(0.U(1.W))))
  val vld0_counter = RegInit(0.U(2.W))
  val vld1_counter = RegInit(0.U(2.W))
  when(io.vld_ph0){
    vld0_counter := 2.U
  }.elsewhen(vld0_counter === 0.U){
    vld0_counter := vld0_counter
  }.otherwise{
    vld0_counter := vld0_counter - 1.U
  }
when(io.vld_ph1){
    vld1_counter := 2.U
  }.elsewhen(vld1_counter === 0.U){
    vld1_counter := vld1_counter
  }.otherwise{
    vld1_counter := vld1_counter - 1.U
  }
  switch(Cat(io.vld_ph1, io.vld_ph0)) {
    is("b01".U) { // load
      when(io.dfi_parameter_mode) {
        when(io.latency(0) === 0.U) {
          shif_bit0(0) := 1.U
          shif_bit0(1) := 1.U
          shif_bit0(2) := 0.U
          shif_bit1(0) := 1.U
          shif_bit1(1) := 1.U
          shif_bit1(2) := 0.U
        }.otherwise {
          shif_bit0(0) := Mux(vld0_counter > 0.U ,1.U,0.U)
          shif_bit0(1) := 1.U
          shif_bit0(2) := 1.U
          shif_bit1(0) := 1.U
          shif_bit1(1) := 1.U
          shif_bit1(2) := 0.U
        }
      }.otherwise {
          shif_bit0(0) := 1.U
          shif_bit0(1) := 1.U
          shif_bit0(2) := 0.U
          shif_bit1(0) := 1.U
          shif_bit1(1) := 1.U
          shif_bit1(2) := 0.U
      }
    }
    is("b10".U) {
      when(io.dfi_parameter_mode) {
        when(io.latency(0) === 0.U) {
          shif_bit0(0) := 1.U
          shif_bit0(1) := 1.U
          shif_bit0(2) := 0.U
          shif_bit1(0) := 1.U
          shif_bit1(1) := 1.U
          shif_bit1(2) := 0.U
        }.otherwise {
          shif_bit0(0) := Mux(vld1_counter > 0.U ,1.U,0.U)
          shif_bit0(1) := 1.U
          shif_bit0(2) := 1.U
          shif_bit1(0) := 1.U
          shif_bit1(1) := 1.U
          shif_bit1(2) := 0.U
        }
      }.otherwise {
          shif_bit0(0) := 1.U
          shif_bit0(1) := 1.U
          shif_bit0(2) := 0.U
          shif_bit1(0) := 1.U
          shif_bit1(1) := 1.U
          shif_bit1(2) := 0.U
      }
    }
    is("b00".U) {
      
        shif_bit0 :=Cat(0.U, shif_bit0(2),shif_bit0(1)).asTypeOf(shif_bit0)
        shif_bit1 :=Cat(0.U, shif_bit1(2),shif_bit1(1)).asTypeOf(shif_bit1)
    }
  }
  offset0  := Mux(vld_delay.asUInt.orR,shif_bit0(0),0.U)
  offset1  := Mux(vld_delay.asUInt.orR,shif_bit1(0),0.U)

//delay
  val Delay0          = RegInit(VecInit.fill(Wlmax)(0.U(1.W)))
  val Delay1          = RegInit(VecInit.fill(Wlmax)(0.U(1.W)))
    for(i <- 1  until  Wlmax){
            Delay0(0) := offset0
            Delay0(i) := (Delay0(i-1))
    }
    for(i <- 1  until  Wlmax){
            Delay1(0) := offset1
            Delay1(i) := (Delay1(i-1))
    }

  // Correct output assignment
  val half_width = width / 2
  val upper_bits = Fill(half_width, Delay1((io.latency>>1)-2.U))
  val lower_bits = Fill(half_width, Delay0((io.latency>>1)-2.U))
  io.offset_out := Cat(upper_bits, lower_bits).asTypeOf(io.offset_out)
}