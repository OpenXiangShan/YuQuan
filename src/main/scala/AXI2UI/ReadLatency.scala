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

class ReadLatency extends Module {
    val io = IO(new Bundle {
        val  arvalid  = Flipped(Bool())
        val  arready  = Flipped(Bool())
        val  rvalid   = Flipped(Bool())
        val  rready   = Flipped(Bool())
        val  rlast    = Flipped(Bool())
    })
    val latency_queue = Module(new Queue(UInt(64.W),512)).io
    val gcount = RegInit(0.U(64.W))
    val readLatency = WireInit(0.U(64.W))
    val transactionNum = RegInit(0.U(64.W))
    val SumLatency     = RegInit(0.U(64.W))
    dontTouch(readLatency)
    dontTouch(SumLatency)
    dontTouch(transactionNum)
    gcount := gcount + 1.U 
    latency_queue.enq.valid := io.arready & io.arvalid
    latency_queue.enq.bits  := gcount 
    latency_queue.deq.ready := io.rready & io.rvalid & io.rlast
    readLatency := Mux(io.rready&io.rvalid&io.rlast,gcount - latency_queue.deq.bits,0.U)
    transactionNum := Mux(io.rready&io.rvalid&io.rlast,transactionNum + 1.U , transactionNum)
    SumLatency   :=Mux(io.rready&io.rvalid&io.rlast,SumLatency + gcount - latency_queue.deq.bits,SumLatency)
}