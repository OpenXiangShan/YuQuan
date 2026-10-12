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
import chisel3.experimental.BundleLiterals._
import BUNDLE_PARAM._
import os.stat

class CmdGenArb extends Bundle {
    val actReq  = Bool()
    val actResp = Flipped(Bool())
    val actRow  = UInt(BUNDLE_PARAM.ABITS.W)
    val actRank = UInt(BUNDLE_PARAM.RANK_WIDTH.W)

    val preReq  = Bool()
    val preRank = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
    val preResp = Flipped(Bool())
    
    val casReq  = Bool()
    val casRank = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
    val casResp = Flipped(Bool())
    val casRead = Bool()
    val casCol  = UInt(BUNDLE_PARAM.COL_WIDTH.W)
    val casTok = UInt(BUNDLE_PARAM.TOKENBITS.W)
}

class CommandGen extends Module{
    val io = IO(new Bundle{
        val calDone        = Flipped(Bool())
        val request        = Flipped(Decoupled(new fifo_adr(3)))
        val requestID      = Flipped(new Bundle {val rank = UInt(RANK_WIDTH.W)
                                                 val BG   = UInt(BG_WIDTH.W)
                                                 val BA   = UInt(BANK_WIDTH.W)})
        val arb            = new CmdGenArb
        val refCtrl        = Flipped(new RefCmdGenIO)
    })


    object FSM extends ChiselEnum{   
        val REF        = 0.U(3.W)  
        val QUERY_ROW  = 1.U(3.W) 
        val PRE_ISSUE  = 2.U(3.W) 
        val ACT_ISSUE  = 3.U(3.W) 
        val CAS_ISSUE  = 4.U(3.W)
    }
    val state  = RegInit(FSM.QUERY_ROW)

    val rowState = RegInit(new Bundle{
        val row   = UInt(BUNDLE_PARAM.ABITS.W)
        val valid  = Bool()
    }.Lit(
        _.row -> 0.U,
        _.valid -> false.B
    ))


    val curReq = Module(new Queue(io.request.bits.cloneType, 1, pipe = true))
    curReq.io.enq :<>= io.request
    curReq.io.deq.ready := false.B


    // state transition logic
    switch(state) {
        is(FSM.QUERY_ROW) {
            when(io.refCtrl.block) {
                state := FSM.REF
            }.elsewhen(curReq.io.deq.valid){
                when(!rowState.valid) {
                    state := FSM.ACT_ISSUE
                }.elsewhen(curReq.io.deq.bits.adr.row === rowState.row)  {
                    state := FSM.CAS_ISSUE
                }.otherwise{
                    state := FSM.PRE_ISSUE 
                }
            }
        }

        is (FSM.PRE_ISSUE) {
            when(io.refCtrl.block) {
                state := FSM.REF
            }.elsewhen(io.arb.preResp) {
                state := FSM.ACT_ISSUE
            }
        }

        is (FSM.ACT_ISSUE) {
            when(io.refCtrl.block) {
                state := FSM.REF
            }.elsewhen(io.arb.actResp) {
                state := FSM.CAS_ISSUE
                rowState.valid := true.B
                rowState.row := curReq.io.deq.bits.adr.row
            }
        }

        is (FSM.CAS_ISSUE) {
            when(io.arb.casResp) {
                state := FSM.QUERY_ROW
                curReq.io.deq.ready := true.B
            }
        }

        is (FSM.REF) {
            rowState.valid := Mux(io.refCtrl.preIss,false.B,rowState.valid)
            when(io.refCtrl.release) {
                state := FSM.QUERY_ROW
            }
        }
    }

    io.arb.actReq := state === FSM.ACT_ISSUE
    io.arb.preReq := state === FSM.PRE_ISSUE
    io.arb.casReq := state === FSM.CAS_ISSUE
    
    io.arb.actRank:= io.requestID.rank
    io.arb.preRank:= io.requestID.rank
    io.arb.casRank:= io.requestID.rank

    io.arb.actRow  := Mux(curReq.io.deq.valid,curReq.io.deq.bits.adr.row     , 0.U.asTypeOf(curReq.io.deq.bits.adr.row))
    io.arb.casRead := Mux(curReq.io.deq.valid,curReq.io.deq.bits.cmdtype     , 0.U.asTypeOf(curReq.io.deq.bits.cmdtype))
    io.arb.casCol  := Mux(curReq.io.deq.valid,curReq.io.deq.bits.adr.col     , 0.U.asTypeOf(curReq.io.deq.bits.adr.col))
    io.arb.casTok  := Mux(curReq.io.deq.valid,curReq.io.deq.bits.adr.cmdToken, 0.U.asTypeOf(curReq.io.deq.bits.adr.cmdToken))
    
    io.refCtrl.ack := state === FSM.REF

    io.request.ready := curReq.io.enq.ready
}