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
import circt.stage._
import chisel3.util._

class RequestQueue (qDepth: Int = 64) extends CacheModule {
    val io = IO(new Bundle {
        // Interface with AddrMap
        val RdCmdFromAddrMap = Flipped(Decoupled(new SplitCmdIO))
        val WrCmdFromAddrMap = Flipped(Decoupled(new SplitCmdIO))
        val WrDataFromAddrMap = Flipped(Decoupled(new WrDataIO))

        // Interface with Pipeline
        val RequestCmd = Decoupled(new SplitCmdIO { val CmdType = Output(Bool())})  // Output cmd add 1bit: --0 write, --1 read
        val RequestwData = Decoupled(new WrDataIO)
    })

    val TOKEN_W = BUNDLE_PARAM.TOKEN_WIDTH  
    val rd_token = io.RdCmdFromAddrMap.bits.token
    val wr_token = io.WrCmdFromAddrMap.bits.token 
    
    
    val readFirst = ((rd_token(TOKEN_W-1) ^ wr_token(TOKEN_W-1)) & (rd_token(TOKEN_W-2,0) > wr_token(TOKEN_W-2,0)))  |   
                   (~(rd_token(TOKEN_W-1) ^ wr_token(TOKEN_W-1)) & (rd_token(TOKEN_W-2,0) < wr_token(TOKEN_W-2,0)))

    val CmdQueue = Module(new Queue(chiselTypeOf(io.RequestCmd.bits), qDepth))
    val DataQueue = Module(new Queue(chiselTypeOf(io.RequestwData.bits), qDepth+3))
    val CmdDefault = 0.U.asTypeOf(io.RequestCmd.bits)
    val DataDefault = 0.U.asTypeOf(io.RequestwData.bits)
    val requestVec = Cat(io.WrCmdFromAddrMap.valid, io.RdCmdFromAddrMap.valid)
    val ChosenRead = Mux((requestVec === 1.U) || ((requestVec === 3.U) && readFirst), true.B, false.B)
    
    // enq
    when(ChosenRead) {
        CmdQueue.io.enq.bits := Cat(io.RdCmdFromAddrMap.bits.asUInt, true.B).asTypeOf(chiselTypeOf(CmdQueue.io.enq.bits))
        CmdQueue.io.enq.valid := io.RdCmdFromAddrMap.valid
        DataQueue.io.enq.valid := false.B
        DataQueue.io.enq.bits := DataDefault

        io.RdCmdFromAddrMap.ready := CmdQueue.io.enq.ready
        io.WrCmdFromAddrMap.ready := false.B
        io.WrDataFromAddrMap.ready := false.B
    }.otherwise {
        CmdQueue.io.enq.valid := io.WrCmdFromAddrMap.valid
        CmdQueue.io.enq.bits := Cat(io.WrCmdFromAddrMap.bits.asUInt, false.B).asTypeOf(chiselTypeOf(CmdQueue.io.enq.bits))
        DataQueue.io.enq.valid := io.WrCmdFromAddrMap.fire
        DataQueue.io.enq.bits := io.WrDataFromAddrMap.bits

        io.WrCmdFromAddrMap.ready := CmdQueue.io.enq.ready //&& DataQueue.io.enq.ready
        io.WrDataFromAddrMap.ready := io.WrCmdFromAddrMap.ready
        io.RdCmdFromAddrMap.ready := false.B 
    }
    val RequestCmdDefault = 0.U.asTypeOf(chiselTypeOf(CmdQueue.io.deq.bits))
    
    // deq
    io.RequestCmd <> CmdQueue.io.deq
    io.RequestwData <> DataQueue.io.deq
    io.RequestCmd.bits := Mux(CmdQueue.io.deq.fire, CmdQueue.io.deq.bits, CmdDefault) 
    io.RequestwData.bits := Mux(DataQueue.io.deq.fire, DataQueue.io.deq.bits, DataDefault)
}