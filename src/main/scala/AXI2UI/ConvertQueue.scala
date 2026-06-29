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
import chisel3.util._
import chisel3.experimental.FlatIO
import java.util.ResourceBundle
class ConvertQueue[T <: Data](narrow : T , depth : Int = 8) extends Module {
  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(Vec(2,narrow)))
    val deq = Decoupled(narrow)
  })
  
  val wideQueue = Module(new Queue(Vec(2,narrow), depth))
  val narrowQueue = Module(new Queue(narrow, depth * 2))
  
  val state = RegInit(false.B)
  val stored_high = RegInit(0.U(narrow.getWidth.W))
  wideQueue.io.enq <> io.enq
  
  val can_split_high = wideQueue.io.deq.valid && narrowQueue.io.enq.ready && !state
  val can_split_low = narrowQueue.io.enq.ready && state
  stored_high := Mux(wideQueue.io.deq.fire, wideQueue.io.deq.bits(1).asTypeOf(stored_high),stored_high)
    when(can_split_high) {
      state := true.B
    }.elsewhen(can_split_low) {
      state := false.B
    }
  
  narrowQueue.io.enq.valid := can_split_high || can_split_low
  when(state) {
    narrowQueue.io.enq.bits := stored_high.asTypeOf(narrowQueue.io.enq.bits)
  }.otherwise {
    narrowQueue.io.enq.bits := wideQueue.io.deq.bits(0)
  }
  
  wideQueue.io.deq.ready := can_split_high
  io.deq <> narrowQueue.io.deq
}