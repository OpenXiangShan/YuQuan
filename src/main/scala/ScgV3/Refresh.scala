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
import BUNDLE_PARAM._


class RefCmdGenIO extends Bundle {
    val block   = Bool()
    val release = Bool()
    val ack     = Flipped(Bool())
    val preIss  = Bool()
}

class RefArbIO extends Bundle {
    val refIss = Bool()
    val zqIss  = Bool()
    val preIss = Bool()
    val preOK = Flipped(Bool())
}
trait RefreshMode{
    def autoRefresh = 0.U
    def postponedRefresh = 1.U
    def speculativeRefresh = 2.U
}

class Refresh extends Module with RefreshMode{
    val io = IO(new Bundle{
        val CalDone = Flipped(Bool())
        val refCmdGen = Vec(1<<(BG_WIDTH+BANK_WIDTH+RANK_WIDTH), new RefCmdGenIO)
        val timeParam    = Flipped(new RefTime)
        val refArb = new RefArbIO
        val SchedulerQueueIsEmpty = Flipped(Bool())
        // val debug_ref_state = UInt(3.W)
    }) 

    //REF_FSM
    object  REF_FSM extends ChiselEnum{
        val IDLE        = 0.U(4.W)
        val BLOCK_REQ   = 1.U(4.W) 
        val PRE_WAIT    = 2.U(4.W)
        val PREISS   = 3.U(4.W) 
        val tRP_WAIT    = 4.U(4.W)
        val REFISS      = 5.U(4.W)
        val tRFC_WAIT   = 6.U(4.W)
        val ZQISS       = 7.U(4.W)
        val ZQCS        = 8.U(4.W)
    }
    val state          = RegInit(REF_FSM.IDLE)
    
    val refPend = RegInit(0.U(5.W))
    val zqPend  = RegInit(false.B)

    // 统一的timer更新函数
    def updateTimer(timer: UInt, resetVal: UInt): UInt = {
        // Mux(io.CalDone & timer === 0.U, resetVal - 1.U, timer - 1.U )
        Mux(io.CalDone,Mux(timer === 0.U ,resetVal - 1.U,timer - 1.U ),resetVal)
    }
    val tREFI_TIMER_default = WireInit((io.timeParam.tREFI * BUNDLE_PARAM.MC_CLK.U )/1000.U)
    val tREFI_TIMER_mux     = MuxCase(tREFI_TIMER_default -1.U,Seq(
        (io.timeParam.RefreshMode === autoRefresh,tREFI_TIMER_default -1.U),
        (io.timeParam.RefreshMode === postponedRefresh,(tREFI_TIMER_default*8.U) -1.U ),
        (io.timeParam.RefreshMode === speculativeRefresh,((tREFI_TIMER_default)-1.U))
    ))

    val tREFI_timer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    tREFI_timer := updateTimer(tREFI_timer, tREFI_TIMER_mux)

    val tZQINTVL_timer_default = (((io.timeParam.tZQINTVL*BUNDLE_PARAM.MC_CLK.U)*1000.U) ) - 1.U
    val tZQINTVL_timer = RegInit(0.U(BUNDLE_PARAM.tZQINTVL_Witdh.W))
    tZQINTVL_timer := updateTimer(tZQINTVL_timer, tZQINTVL_timer_default)

    val tRP_timer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    when(state === REF_FSM.tRP_WAIT) {
        tRP_timer := updateTimer(tRP_timer, ((io.timeParam.tRP + 1.U) >> 1) - 1.U)
    }.otherwise {
        tRP_timer := ((io.timeParam.tRP + 1.U) >> 1) - 1.U
    }

    val tRFC_timer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    when(state === REF_FSM.tRFC_WAIT) {
        tRFC_timer := updateTimer(tRFC_timer, (((io.timeParam.tRFC*BUNDLE_PARAM.MC_CLK.U)/1000.U)) - 1.U)
    }.otherwise {
        tRFC_timer := (((io.timeParam.tRFC*BUNDLE_PARAM.MC_CLK.U)/1000.U)) - 1.U
    }

    val tZQCS_timer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    when(state === REF_FSM.ZQCS) {
        tZQCS_timer := updateTimer(tZQCS_timer, io.timeParam.tZQCS)
    }.otherwise {
        tZQCS_timer := io.timeParam.tZQCS - 1.U
    }

    //busrt refresh
    val busrt_refresh_count = RegInit(0.U(5.W))
    busrt_refresh_count := busrt_refresh_count //default 

    // ref pend
    when (state === REF_FSM.REFISS) {
        refPend := Mux(refPend > 0.U ,refPend - 1.U,refPend)
    }.elsewhen(io.CalDone && tREFI_timer === 0.U) {
        refPend := refPend + 1.U 
        busrt_refresh_count := Mux(io.timeParam.RefreshMode === autoRefresh ,0.U ,8.U)
    }

    // zq pend
    when (state === REF_FSM.ZQISS) {
        zqPend := false.B 
    }.elsewhen(io.CalDone && tZQINTVL_timer === 0.U) {
        zqPend := true.B 
    }
    //refresh condition
    val refCond  = WireInit(false.B)
    refCond := MuxCase(false.B,Seq(
        (io.timeParam.RefreshMode === autoRefresh,refPend.orR),
        (io.timeParam.RefreshMode === postponedRefresh,refPend.orR),
        (io.timeParam.RefreshMode === speculativeRefresh,(refPend.orR & io.SchedulerQueueIsEmpty) | refPend === 8.U)
    ))
    switch (state) {
        is (REF_FSM.IDLE) {
            when (refCond || zqPend) {
                state := REF_FSM.BLOCK_REQ
            }
        }

        is (REF_FSM.BLOCK_REQ) {
            when(io.timeParam.RefreshMode === speculativeRefresh & refPend =/= 8.U & !io.SchedulerQueueIsEmpty){ // interupt refresh
                state := REF_FSM.IDLE
            }.elsewhen(io.refCmdGen.map(_.ack).reduce(_ && _)) { 
                state := REF_FSM.PRE_WAIT  
            }
        }

        is (REF_FSM.PRE_WAIT) {
            when(io.timeParam.RefreshMode === speculativeRefresh & refPend =/= 8.U & !io.SchedulerQueueIsEmpty & !io.refArb.preOK){ // interupt refresh
                state := REF_FSM.IDLE
            }.elsewhen (io.refArb.preOK) {
                state := REF_FSM.PREISS
            }
        }

        is (REF_FSM.PREISS) {
            state := REF_FSM.tRP_WAIT
        }
        
        is (REF_FSM.tRP_WAIT) {
            when (tRP_timer === 0.U) {
                when (refPend.orR) {
                    state := REF_FSM.REFISS
                }.elsewhen(zqPend) {
                    state := REF_FSM.ZQISS
                }
            }
        }

        is (REF_FSM.REFISS) {
           state := REF_FSM.tRFC_WAIT 
           busrt_refresh_count := Mux(busrt_refresh_count === 0.U ,busrt_refresh_count ,busrt_refresh_count -1.U )
        }

        is (REF_FSM.tRFC_WAIT) {
           when (tRFC_timer === 0.U) {
                when(Mux(io.timeParam.RefreshMode ===autoRefresh | io.timeParam.RefreshMode === postponedRefresh ,busrt_refresh_count === 0.U,refPend === 0.U)){
                    when (zqPend) {
                        state := REF_FSM.ZQISS
                    }.otherwise {
                        state := REF_FSM.IDLE
                    }
                }.otherwise{
                        state := REF_FSM.REFISS
                }       
               
           }
        }

        is (REF_FSM.ZQISS) { 
            state := REF_FSM.ZQCS
        }

        is (REF_FSM.ZQCS) {
            assert (!zqPend)
            when (tZQCS_timer === 0.U) {
                when (refPend.orR) {
                    state := REF_FSM.REFISS
                }.otherwise {
                    state := REF_FSM.IDLE
                }
            } 
        }
    }

    io.refCmdGen.foreach(rg =>  {
        rg.block   := state === REF_FSM.BLOCK_REQ
        rg.release := state === REF_FSM.IDLE
    })

    io.refArb.preIss := state === REF_FSM.PREISS
    io.refCmdGen.foreach(ba => {
        ba.preIss := state === REF_FSM.PREISS
    })
    io.refArb.refIss := state === REF_FSM.REFISS
    io.refArb.zqIss  := state === REF_FSM.ZQISS
}