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
import ujson.False
import APB.SCGRegIO
import APB.APBSlvtop
import javax.print.DocFlavor.READER
import scribe.ANSI.bg
import java.util.concurrent.Flow
import os.stat
import chisel3.experimental.BundleLiterals._

class TimingDFIPhase extends Bundle {
    // priority: act > cas > pre
    val actReq = Bool()  
    val actBg  = UInt(BGBITS.W)
    val actBa  = UInt(BABITS.W)
    val actRow = UInt(BUNDLE_PARAM.ABITS.W)
    val actRank= UInt(BUNDLE_PARAM.RANK_WIDTH.W)

    val casReq = Bool()
    val casBg  = UInt(BGBITS.W)
    val casBa  = UInt(BABITS.W)
    val casCol = UInt(BUNDLE_PARAM.COL_WIDTH.W)
    val casTok = UInt(BUNDLE_PARAM.TOKENBITS.W)
    val casRead= Bool()
    val casRank= UInt(BUNDLE_PARAM.RANK_WIDTH.W)

    val preReq = Bool()
    val preBg  = UInt(BGBITS.W)
    val preBa  = UInt(BABITS.W)
    val preRank= UInt(BUNDLE_PARAM.RANK_WIDTH.W)

    val refIss = Bool()
    val zqIss  = Bool()
    val preIss = Bool()
}

class TimingArb extends Module{
    val allBankNum = 1 << (BGBITS + BABITS + RANK_WIDTH)
    val bgNum = 1 << BGBITS

    val io = IO (new Bundle {
        val cmdGen     = Vec(allBankNum, Flipped(new CmdGenArb))
        val phaseCtrl  = new TimingDFIPhase
        val refresh    = Flipped(new RefArbIO)
        val parameters = Input(new TCtime)
    })

// ======================================================================================
//                       Timing Counters
// ======================================================================================
    val rrdlTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val rrdsTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val fawWindow_rank0 = RegInit(0.U(32.W))//max tFAW = 64
    val fawWindow_rank1 = RegInit(0.U(32.W))//max tFAW = 64

    val ccdlTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val ccdsTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))

    val wtrlTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val wtrsTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))

    val rtwTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))

    val wtrDiffRankTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val wtwDiffRankTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val rtwDiffRankTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val rtrDiffRankTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))

    val wrTimer  = RegInit(VecInit.fill(allBankNum)(0.U(BUNDLE_PARAM.McParamWidth.W)))
    val rtpTimer = RegInit(VecInit.fill(allBankNum)(0.U(BUNDLE_PARAM.McParamWidth.W)))
    val rasTimer = RegInit(VecInit.fill(allBankNum)(0.U(BUNDLE_PARAM.McParamWidth.W)))
    val rcdTimer = RegInit(VecInit.fill(allBankNum)(0.U(BUNDLE_PARAM.McParamWidth.W)))
    val rpTimer  = RegInit(VecInit.fill(allBankNum)(0.U(BUNDLE_PARAM.McParamWidth.W)))

    val fawOK_rank0 = Wire(Bool())
    val fawOK_rank1 = Wire(Bool())


    // refresh timing counters
    val rtpaTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val rasaTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))
    val wtpaTimer = RegInit(0.U(BUNDLE_PARAM.McParamWidth.W))


// ======================================================================================
//                      Check Timing, Feed Arbiters
// ======================================================================================
    val actArb = Module(new RoundRobinArbiter(allBankNum))
    val preArb = Module(new RoundRobinArbiter(allBankNum))
    val casArb = Module(new RoundRobinArbiter(allBankNum))
    val FlowCtrl = Module(new FlowCtrl(allBankNum))

// ======================================================================================
//                      FlowCtrl:select one from actArb 、preArb 、casArb 
// ======================================================================================
    FlowCtrl.io.act_req         := actArb.io.valid
    FlowCtrl.io.act_chosen      := actArb.io.chosen
    FlowCtrl.io.cas_req         := casArb.io.valid
    FlowCtrl.io.cas_chosen      := casArb.io.chosen
    FlowCtrl.io.pre_req         := preArb.io.valid
    FlowCtrl.io.pre_chosen      := preArb.io.chosen

    val hasAct = WireInit(FlowCtrl.io.act_won)
    val hasActRank = WireInit(FlowCtrl.io.act_chosen_won(BGBITS + BABITS + RANK_WIDTH - 1 ,BGBITS + BABITS))
    val hasCas = WireInit(FlowCtrl.io.cas_won)
    val hasPre = WireInit(FlowCtrl.io.pre_won)

    val preChosen = WireInit(FlowCtrl.io.pre_chosen_won)
    val actChosen = WireInit(FlowCtrl.io.act_chosen_won)
    val casChosen = WireInit(FlowCtrl.io.cas_chosen_won)

    //transcribe last act rank and bg
    val PreviousAct = RegInit(new Bundle{
        val rank = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
        val bg   = UInt(BUNDLE_PARAM.BG_WIDTH.W)
    }.Lit(
        _.rank -> 0.U ,
        _.bg   -> 0.U
    ))
    PreviousAct.rank := Mux(io.phaseCtrl.actReq ,io.phaseCtrl.actRank , PreviousAct.rank)
    PreviousAct.bg   := Mux(io.phaseCtrl.actReq ,io.phaseCtrl.actBg   , PreviousAct.bg  )
    //transcribe last cas rank and bg 
    val PreviousCas = RegInit(new Bundle {
        val rank = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
        val bg   = UInt(BUNDLE_PARAM.BG_WIDTH.W)
    }.Lit(
        _.rank -> 0.U ,
        _.bg   -> 0.U 
    ))
    PreviousCas.rank := Mux(io.phaseCtrl.casReq ,io.phaseCtrl.casRank , PreviousCas.rank)
    PreviousCas.bg   := Mux(io.phaseCtrl.casReq ,io.phaseCtrl.casBg   , PreviousCas.bg  ) 
    //transcribe last write rank and bg
    val PreviousWrite = RegInit(new Bundle{
        val rank = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
        val bg   = UInt(BUNDLE_PARAM.BG_WIDTH.W)
    }.Lit(
        _.rank -> 0.U ,
        _.bg   -> 0.U 
    ))
    PreviousWrite.rank := Mux(io.phaseCtrl.casReq & (!io.phaseCtrl.casRead)  ,io.phaseCtrl.casRank , PreviousWrite.rank)
    PreviousWrite.bg   := Mux(io.phaseCtrl.casReq & (!io.phaseCtrl.casRead)  ,io.phaseCtrl.casBg   , PreviousWrite.bg)
    //timing check
    def checkAct(idx: Int, sameBG : Bool , rank : Int): Bool = {
        Mux(sameBG,rrdlTimer === 0.U ,rrdsTimer === 0.U)&& rpTimer(idx) === 0.U && Mux(rank.U === 1.U ,fawOK_rank1,fawOK_rank0 )
    }

    def checkPre(idx: Int, bg:Int, ba:Int): Bool = {
        rtpTimer(idx) === 0.U && rasTimer(idx) === 0.U && wrTimer(idx) === 0.U
    }

    def checkCas(idx: Int, sameRank: Bool ,sameBG: Bool , WRsameBG : Bool, read:Bool): Bool = {
        rcdTimer(idx) === 0.U & Mux(sameRank , Mux(sameBG , ccdlTimer === 0.U , ccdsTimer === 0.U ) & Mux(read , Mux(WRsameBG,wtrlTimer === 0.U , wtrsTimer === 0.U), rtwTimer === 0.U ),
                                               Mux(read,rtrDiffRankTimer === 0.U & wtrDiffRankTimer === 0.U & wtrlTimer === 0.U , wtwDiffRankTimer === 0.U & rtwDiffRankTimer === 0.U & rtwTimer === 0.U))
    }

    io.cmdGen.zipWithIndex.foreach {
        case (cmd, i) => {
            val bg = (i >> BABITS) & ((1 << BGBITS) - 1)
            val ba = i & ((1 << BABITS) - 1)
            val rank = i >>(BABITS + BGBITS)
            actArb.io.requests(i) := cmd.actReq & checkAct(i, bg.U === PreviousAct.bg & rank.U === PreviousAct.rank,rank)
            preArb.io.requests(i) := cmd.preReq & checkPre(i, bg, ba)
            casArb.io.requests(i) := cmd.casReq & checkCas(i, rank.U === PreviousCas.rank, bg.U === PreviousCas.bg, bg.U === PreviousWrite.bg, cmd.casRead) 
        } 
    }

// ======================================================================================
//                   latch cmds to DFI one cycle
// ======================================================================================
    val cmdGens = WireInit(io.cmdGen)
    val actReqR = RegNext(hasAct)
    val actRank = RegNext(actChosen(RANK_WIDTH + BGBITS + BABITS - 1 ,BGBITS + BABITS))
    val actBgR  = RegNext(actChosen(BABITS + BGBITS - 1, BABITS))
    val actBaR  = RegNext(actChosen(BABITS -1 , 0))
    val actRowR = RegNext(cmdGens(actChosen).actRow)
    

    val preReqR = RegNext(hasPre)
    val preBgR  = RegNext(preChosen(BABITS + BGBITS - 1, BABITS))
    val preBaR  = RegNext(preChosen(BABITS -1 , 0))
    val preRank = RegNext(preChosen(RANK_WIDTH + BGBITS + BABITS - 1 ,BGBITS + BABITS))

    val casReqR = RegNext(hasCas)
    val casBgR  = RegNext(casChosen(BABITS + BGBITS - 1, BABITS))
    val casBaR  = RegNext(casChosen(BABITS -1 , 0))
    val casColR = RegNext(cmdGens(casChosen).casCol)
    val casTokR = RegNext(cmdGens(casChosen).casTok)
    val casReadR= RegNext(cmdGens(casChosen).casRead)
    val casRank = RegNext(casChosen(RANK_WIDTH + BGBITS + BABITS - 1 ,BGBITS + BABITS))

    val refIssR = RegNext(io.refresh.refIss)
    val zqIssR  = RegNext(io.refresh.zqIss)
    val preIssR = RegNext(io.refresh.preIss)


    io.phaseCtrl.actReq := actReqR
    io.phaseCtrl.actBg  := actBgR
    io.phaseCtrl.actBa  := actBaR
    io.phaseCtrl.actRow := actRowR
    io.phaseCtrl.actRank:= actRank

    io.phaseCtrl.casReq := casReqR
    io.phaseCtrl.casBg  := casBgR
    io.phaseCtrl.casBa  := casBaR
    io.phaseCtrl.casCol := casColR
    io.phaseCtrl.casTok := casTokR
    io.phaseCtrl.casRead:= casReadR
    io.phaseCtrl.casRank:= casRank

    io.phaseCtrl.preReq := preReqR
    io.phaseCtrl.preBg  := preBgR
    io.phaseCtrl.preBa  := preBaR
    io.phaseCtrl.preRank:= preRank

    io.phaseCtrl.refIss := refIssR
    io.phaseCtrl.zqIss  := zqIssR
    io.phaseCtrl.preIss := preIssR
// ======================================================================================
//                              刷新的pre时序
// ======================================================================================
    io.refresh.preOK := rtpaTimer === 0.U && rasaTimer === 0.U && wtpaTimer === 0.U
// ======================================================================================
//                         act,cas,pre三路仲裁选择两路,简化后续设计
// ======================================================================================

    actArb.io.accept := hasAct
    casArb.io.accept := hasCas
    preArb.io.accept := hasPre

    for (i <- 0 until cmdGens.length) {
        cmdGens(i).actResp := false.B
        cmdGens(i).casResp := false.B
        cmdGens(i).preResp := false.B

        when(i.U === actChosen) {
            cmdGens(i).actResp := hasAct
        }

        when(i.U === casChosen) {
            cmdGens(i).casResp := hasCas 
        }

        when(i.U === preChosen) {
            cmdGens(i).preResp := hasPre 
        }
    }
// ======================================================================================
//                      latch timing parameters
// ======================================================================================
    val tFAW  = RegNext((io.parameters.tFAW   + 1.U) >> 1) - 2.U
    val tRRDS = RegNext((io.parameters.tRRD_S + 1.U) >> 1) - 1.U
    val tRRDL = RegNext((io.parameters.tRRD_L + 1.U) >> 1) - 1.U
    val tRAS  = RegNext((io.parameters.tRAS   + 1.U) >> 1) - 1.U
    val tRCD  = RegNext((io.parameters.tRCD   + 1.U) >> 1) - 1.U

    val tRP0  = RegNext((io.parameters.tRP + 1.U) >> 1) - 1.U
    val tRP1  = RegNext((io.parameters.tRP + 1.U) >> 1)

    val tCCDS0 = RegNext((io.parameters.tCCD_S + 1.U) >> 1) - 1.U
    val tCCDL0 = RegNext((io.parameters.tCCD_L + 1.U) >> 1) - 1.U
    val tWTRS0 = RegNext((io.parameters.tWTR_S + io.parameters.WL + (io.parameters.BL >> 1) + 1.U) >> 1) - 1.U
    val tWTRL0 = RegNext((io.parameters.tWTR_L + io.parameters.WL + (io.parameters.BL >> 1) + 1.U) >> 1) - 1.U
    val tRTW0  = RegNext((io.parameters.tRTW   + 1.U) >> 1) - 1.U
    val tRTP0  = RegNext((io.parameters.tRTP   + 1.U) >> 1) - 1.U
    val tWTP0  = RegNext((io.parameters.tWR + io.parameters.WL + (io.parameters.BL >> 1) + 1.U) >> 1) - 1.U
    val tWR0   = RegNext(((io.parameters.tWR + io.parameters.WL+(io.parameters.BL >> 1))    + 1.U) >> 1) - 1.U

    val tCCDS1 = RegNext((io.parameters.tCCD_S + 1.U) >> 1)
    val tCCDL1 = RegNext((io.parameters.tCCD_L + 1.U) >> 1)
    val tWTRS1 = RegNext((io.parameters.tWTR_S + io.parameters.WL + (io.parameters.BL >> 1) + 1.U) >> 1)
    val tWTRL1 = RegNext((io.parameters.tWTR_L + io.parameters.WL + (io.parameters.BL >> 1) + 1.U) >> 1)
    val tRTW1  = RegNext((io.parameters.tRTW   + 1.U) >> 1)
    val tRTP1  = RegNext((io.parameters.tRTP   + 1.U) >> 1)
    val tWTP1  = RegNext((io.parameters.tWR + io.parameters.WL + (io.parameters.BL >> 1) + 1.U) >> 1)
    val tWR1   = RegNext(((io.parameters.tWR + io.parameters.WL+(io.parameters.BL >> 1))    + 1.U) >> 1)

    val tWTWDIFFRANK0 = RegNext((io.parameters.tW2WDR + 1.U) >> 1) - 1.U
    val tWTRDIFFRANK0 = RegNext((io.parameters.tW2RDR + 1.U) >> 1) - 1.U
    val tRTRDIFFRANK0 = RegNext((io.parameters.tR2RDR + 1.U) >> 1) - 1.U
    val tRTWDIFFRANK0 = RegNext((io.parameters.tR2WDR + 1.U) >> 1) - 1.U

    val tWTWDIFFRANK1 = RegNext((io.parameters.tW2WDR + 1.U) >> 1) 
    val tWTRDIFFRANK1 = RegNext((io.parameters.tW2RDR + 1.U) >> 1) 
    val tRTRDIFFRANK1 = RegNext((io.parameters.tR2RDR + 1.U) >> 1) 
    val tRTWDIFFRANK1 = RegNext((io.parameters.tR2WDR + 1.U) >> 1) 
// ======================================================================================
//                      FAW Window Update
// ======================================================================================
    //for different ranks ,the FAW window should be separated 
    val actCnt_rank0 = RegInit(0.U(3.W))
    val high_rank0 = VecInit(fawWindow_rank0.asBools)(tFAW)
    switch (Cat(high_rank0, hasAct & hasActRank === 0.U)) {
        is (1.U) {
            actCnt_rank0 := actCnt_rank0 + 1.U
        }

        is (2.U) {
            actCnt_rank0 := actCnt_rank0 - 1.U
        }
    }
    fawOK_rank0 := actCnt_rank0 < 4.U
    fawWindow_rank0 := (fawWindow_rank0 << 1) | (hasAct & hasActRank === 0.U)

    val actCnt_rank1 = RegInit(0.U(3.W))
    val high_rank1 = VecInit(fawWindow_rank1.asBools)(tFAW)
    switch (Cat(high_rank1, hasAct & hasActRank === 1.U)) {
        is (1.U) {
            actCnt_rank1 := actCnt_rank1 + 1.U
        }

        is (2.U) {
            actCnt_rank1 := actCnt_rank1 - 1.U
        }
    }
    fawOK_rank1 := actCnt_rank1 < 4.U
    fawWindow_rank1 := (fawWindow_rank1 << 1) | (hasAct & hasActRank === 1.U)
// ======================================================================================
//                     updating  Timing Counters
// ======================================================================================
    rrdlTimer := Mux(rrdlTimer === 0.U , 0.U ,rrdlTimer - 1.U)
    rrdsTimer := Mux(rrdsTimer === 0.U , 0.U ,rrdsTimer - 1.U)
    ccdlTimer := Mux(ccdlTimer === 0.U , 0.U ,ccdlTimer - 1.U)
    ccdsTimer := Mux(ccdsTimer === 0.U , 0.U ,ccdsTimer - 1.U)
    wtrlTimer := Mux(wtrlTimer === 0.U , 0.U ,wtrlTimer - 1.U)
    wtrsTimer := Mux(wtrsTimer === 0.U , 0.U ,wtrsTimer - 1.U)
    rtwTimer := Mux(rtwTimer === 0.U, 0.U, rtwTimer - 1.U)

    wrTimer.zipWithIndex.foreach{ case (timer, i) =>
        timer := Mux(timer === 0.U, 0.U, timer - 1.U)
    }

    rtpTimer.zipWithIndex.foreach{ case (timer, i) =>
        timer := Mux(timer === 0.U, 0.U, timer - 1.U)
    }

    rasTimer.zipWithIndex.foreach{ case (timer, i) =>
        timer := Mux(timer === 0.U, 0.U, timer - 1.U)
    }

    rcdTimer.zipWithIndex.foreach{ case (timer, i) =>
        timer := Mux(timer === 0.U, 0.U, timer - 1.U)
    }

    rpTimer.zipWithIndex.foreach{ case (timer, i) =>
        timer := Mux(timer === 0.U, 0.U, timer - 1.U)
    }

    rtpaTimer := Mux(rtpaTimer === 0.U, 0.U, rtpaTimer - 1.U)
    rasaTimer := Mux(rasaTimer === 0.U, 0.U, rasaTimer - 1.U)
    wtpaTimer := Mux(wtpaTimer === 0.U, 0.U, wtpaTimer - 1.U)

    wtwDiffRankTimer := Mux(wtwDiffRankTimer === 0.U , 0.U ,wtwDiffRankTimer - 1.U)
    wtrDiffRankTimer := Mux(wtrDiffRankTimer === 0.U , 0.U ,wtrDiffRankTimer - 1.U)
    rtrDiffRankTimer := Mux(rtrDiffRankTimer === 0.U , 0.U ,rtrDiffRankTimer - 1.U)
    rtwDiffRankTimer := Mux(rtwDiffRankTimer === 0.U , 0.U ,rtwDiffRankTimer - 1.U)

    // act > cas > pre
    // if there is only one cmd, then it is always in slot0
    // act always in slot0
    // if has others, pre always in slot1
    // if has act, cas in slot1, otherwise in slot0
    val casBg = casChosen(BGBITS + BABITS - 1, BABITS)
    val casRead = io.cmdGen(casChosen).casRead


    val actBg = actChosen(BGBITS + BABITS - 1, BABITS)
    // priority: act > cas > pre
    switch (Cat(hasAct, hasPre, hasCas)) { // 001, 010, 011, 100, 101, 110
        is ("b001".U) { // just cas
            ccdsTimer := tCCDS0
            ccdlTimer := tCCDL0
            when(casRead) {
                rtpTimer(casChosen) := tRTP0
                rtwTimer := tRTW0
                rtpaTimer := tRTP0
                rtrDiffRankTimer := tRTRDIFFRANK0
                rtwDiffRankTimer := tRTWDIFFRANK0
            }.otherwise { // 
                wrTimer(casChosen) := tWR0
                wtrlTimer := tWTRL0
                wtrsTimer := tWTRS0
                wtpaTimer := tWTP0
                wtrDiffRankTimer := tWTRDIFFRANK0
                wtwDiffRankTimer := tWTWDIFFRANK0
            }
        }

        is ("b010".U) { // just pre
            rpTimer(preChosen) := tRP0
        }

        is ("b011".U) { // cas and pre
            ccdsTimer := tCCDS0
            ccdlTimer := tCCDL0
            when(casRead) {
                rtpTimer(casChosen) := tRTP0
                rtwTimer := tRTW0
                rtpaTimer := tRTP0
                rtrDiffRankTimer := tRTRDIFFRANK0
                rtwDiffRankTimer := tRTWDIFFRANK0
            }.otherwise { // 
                wrTimer(casChosen) := tWR0
                wtrlTimer   := tWTRL0
                wtrsTimer   := tWTRS0
                wtpaTimer := tWTP0
                wtrDiffRankTimer := tWTRDIFFRANK0
                wtwDiffRankTimer := tWTWDIFFRANK0
            }

            rpTimer(preChosen) := tRP1
        }

        is ("b100".U) { // just act
            rrdsTimer   := tRRDS
            rrdlTimer   := tRRDL
            rasTimer(actChosen) := tRAS
            rcdTimer(actChosen) := tRCD
            rasaTimer := tRAS
        }

        is ("b101".U) { // act and cas
            rrdsTimer  := tRRDS
            rrdlTimer  := tRRDL
            rasTimer(actChosen) := tRAS
            rcdTimer(actChosen) := tRCD
            rasaTimer := tRAS

            ccdsTimer  := tCCDS1
            ccdlTimer  := tCCDL1
            when(casRead) {
                rtpTimer(casChosen) := tRTP1
                rtwTimer := tRTW1
                rtpaTimer := tRTP1
                rtrDiffRankTimer := tRTRDIFFRANK1
                rtwDiffRankTimer := tRTWDIFFRANK1
            }.otherwise { // 
                wrTimer(casChosen) := tWR1
                wtrlTimer          := tWTRL1
                wtrsTimer          := tWTRS1
                wtpaTimer := tWTP1
                wtrDiffRankTimer := tWTRDIFFRANK1
                wtwDiffRankTimer := tWTWDIFFRANK1
            }
        }

        is ("b110".U) { // act and pre
            rrdsTimer     := tRRDS
            rrdlTimer     := tRRDL
            rasTimer(actChosen) := tRAS
            rcdTimer(actChosen) := tRCD
            rasaTimer := tRAS

            rpTimer(preChosen) := tRP1
        }
    }
}