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

class CmdGenStatis extends Bundle {
    val actCnt = UInt(32.W)
    val preCnt = UInt(32.W)
    val casCnt = UInt(32.W)
    val clspgZeroCnt = UInt(32.W)
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
        val clspgTmInit    = Input(UInt(9.W))
        val prePolicy      = Input(UInt(2.W))
        val pendingSameRowHit = Input(Bool())
        val openRowState   = Valid(UInt(BUNDLE_PARAM.ROW_WIDTH.W))
        val statis         = Output(new CmdGenStatis)
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

    //performance counter
    val HitNumPerBA = RegInit(0.U(32.W))
    val RsqNumPerBA = RegInit(0.U(32.W))
    dontTouch(HitNumPerBA)
    dontTouch(RsqNumPerBA)    
    val clspage_timer = RegInit(0.U(9.W))

    // command counters
    val statis = RegInit(new CmdGenStatis().Lit(
        _.actCnt -> 0.U,
        _.preCnt -> 0.U,
        _.casCnt -> 0.U,
        _.clspgZeroCnt -> 0.U
    ))
    dontTouch(statis)

    // Close-page policy:
    // prePolicy = 0: open-page (never auto-precharge)
    // prePolicy = 1: static timeout close-page (count down, reset on CAS)
    // prePolicy = 2: dynamic close-page (TODO)
    val timerEnable = io.prePolicy === 1.U
    val incomingSameRowHit = io.request.valid && rowState.valid && io.request.bits.adr.row === rowState.row

    when(rowState.valid && timerEnable) {
        when(state === FSM.CAS_ISSUE && io.arb.casResp) {
            clspage_timer := io.clspgTmInit
        }.elsewhen(clspage_timer =/= 0.U) {
            clspage_timer := clspage_timer - 1.U
        }
    }.otherwise {
        clspage_timer := io.clspgTmInit
    }

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
                    HitNumPerBA := HitNumPerBA + 1.U // hit counter add 
                }.otherwise{
                    state := FSM.PRE_ISSUE 
                }
            }.elsewhen(rowState.valid && timerEnable && clspage_timer === 0.U && !io.pendingSameRowHit && !incomingSameRowHit) {
                state := FSM.PRE_ISSUE
                statis.clspgZeroCnt := statis.clspgZeroCnt + 1.U
            }
        }

        is (FSM.PRE_ISSUE) {
            when(io.refCtrl.block) {
                state := FSM.REF
            }.elsewhen(io.arb.preResp) {
                state := Mux(curReq.io.deq.valid, FSM.ACT_ISSUE, FSM.QUERY_ROW)
                rowState.valid := false.B
                statis.preCnt := statis.preCnt + 1.U
            }
        }

        is (FSM.ACT_ISSUE) {
            when(io.refCtrl.block) {
                state := FSM.REF
            }.elsewhen(io.arb.actResp) {
                state := FSM.CAS_ISSUE
                rowState.valid := true.B
                rowState.row := curReq.io.deq.bits.adr.row
                statis.actCnt := statis.actCnt + 1.U
            }
        }

        is (FSM.CAS_ISSUE) {
            when(io.arb.casResp) {
                state := FSM.QUERY_ROW
                curReq.io.deq.ready := true.B
                statis.casCnt := statis.casCnt + 1.U
                RsqNumPerBA := RsqNumPerBA + 1.U // request counter add
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
    io.openRowState.valid := rowState.valid
    io.openRowState.bits := rowState.row

    io.statis.actCnt := statis.actCnt
    io.statis.preCnt := statis.preCnt
    io.statis.casCnt := statis.casCnt
    io.statis.clspgZeroCnt := statis.clspgZeroCnt

    io.request.ready := curReq.io.enq.ready
}
