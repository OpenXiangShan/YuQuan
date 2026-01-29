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
import utils._

class WCBEntry extends Bundle {
    val req = new WCBReqBundle
    val isMatch = Bool()
    val isValid = Bool()
}

class WCB_by (wcb_depth: Int = 18) extends Module with CacheConst{ 
    //val CacheLineBits = 512
    val token_width = 10
    val io = IO(new Bundle {
        // req from main pipe
        val mpMissReq = Flipped(Decoupled(new WCBReqBundle)) 

        // TODO: add search WCB logic 
        val mpSearchReq = Flipped(Decoupled(new SplitCmdIO{ val CmdType = Output(Bool())}))
        val clearConflict = Flipped(Decoupled(new WrDataIO { val overWrite = Output(Bool())}))
        val isMatch = Output(Bool())
        val isMatchData = Output(UInt(CacheLineBits.W))

        // req to AS 
        val asReq = Decoupled(new SplitCmdIO)
        val asData = Decoupled(new WrDataIO)

    })
    dontTouch(io)     // Don't delete!!!
    // storage 
    val WCBInit = Wire(Vec(wcb_depth,new WCBEntry))
    for (i <- 0 until wcb_depth) { WCBInit(i) := 0.U.asTypeOf(new WCBEntry) }//i <- 0 until wcb_depth 表示从0到wcb_depth赋值给i
    val WCBRegs = RegInit(WCBInit)

    // store logic 
    // val WCBIdx = RegInit(0.U(log2Up(wcb_depth).W))
    // val WCBPtr = RegInit(0.U(log2Up(wcb_depth).W))
    val WCBIdx = Wire(UInt(log2Up(wcb_depth).W))
    val WCBPtr = Wire(UInt(log2Up(wcb_depth).W))
    

    when(io.mpMissReq.fire) { WCBRegs(WCBIdx).req := io.mpMissReq.bits }

    when(io.mpMissReq.fire) { 
        WCBRegs(WCBIdx).isValid := true.B 
        WCBRegs(WCBIdx).isMatch := false.B 
    } 
    when (io.asReq.fire) { WCBRegs(WCBPtr).isValid := false.B }
    
    //Find valid and invalid BitMap
    // val validBitMap = RegInit(VecInit(Seq.fill(wcb_depth)(false.B)))
    // val matchBitMap = RegInit(VecInit(Seq.fill(wcb_depth)(false.B)))
    val validBitMap = VecInit(Seq.fill(wcb_depth)(false.B))
    val matchBitMap = VecInit(Seq.fill(wcb_depth)(false.B))
    WCBRegs.zipWithIndex.map { case(w, i) =>
        validBitMap(i) := w.isValid
        matchBitMap(i) := w.isMatch
    }
    // Search conflict 
    // 1. mianPipe miss Req
    val missReqConfBitMap = VecInit(Seq.fill(wcb_depth)(false.B))
    val missReqConflict = missReqConfBitMap.asUInt.orR && io.mpMissReq.fire
    when(io.mpMissReq.fire){
        WCBRegs.zipWithIndex.map { case(w, i) =>
            missReqConfBitMap(i) := w.isValid && (Cat(w.req.tag, w.req.set, w.req.bank) === Cat(io.mpMissReq.bits.tag, io.mpMissReq.bits.set, io.mpMissReq.bits.bank))
        }
    }
    // val WCBIdx = UInt(0.(log2Up(wcb_depth).W))
    // val WCBPtr = UInt(0.(log2Up(wcb_depth).W)) 
    WCBIdx := Mux(missReqConflict, PriorityEncoder(missReqConfBitMap.asUInt), PriorityEncoder(~validBitMap.asUInt))        // find invalid index to receive mainPipeline req
    WCBPtr := PriorityEncoder(validBitMap.asUInt & (~matchBitMap.asUInt))        // find valid ptr to send req to AS
    //WCBPtr := PriorityEncoder((validBitMap & (~matchBitMap)).asUInt)
    val isFull = (~validBitMap.asUInt) === 0.U 
    val isEmpty = (validBitMap.asUInt & (~matchBitMap.asUInt)) === 0.U
    val validBitMapUInt = validBitMap.asUInt
    val unmatchBitMapUInt = ~matchBitMap.asUInt
    val canGoUInt = (validBitMap.asUInt & (~matchBitMap.asUInt))

    // Search conflict 
    // 2. mainPipe Req
    val conflictBitMap = VecInit(Seq.fill(wcb_depth+1)(false.B))    // TODO: Highest bit, on fire conflict    
    val onFireConflict = conflictBitMap(wcb_depth)
    val conflictIdx = RegEnable(Mux(onFireConflict, WCBIdx, PriorityEncoder(conflictBitMap.asUInt)), io.isMatch)
    io.mpSearchReq.ready := true.B
    when(io.mpSearchReq.fire){
        WCBRegs.zipWithIndex.map { case(w, i) =>
            conflictBitMap(i) := w.isValid && (Cat(w.req.tag, w.req.set, w.req.bank, 0.U(OffsetBits.W)) === getAddr((io.mpSearchReq.asUInt >> 1).asTypeOf(new SplitCmdIO)))
        }
        conflictBitMap(wcb_depth) := Mux(io.mpMissReq.fire, 
                                        Cat(io.mpMissReq.bits.tag, io.mpMissReq.bits.set, io.mpMissReq.bits.bank, 0.U(OffsetBits.W)) === getAddr((io.mpSearchReq.asUInt >> 1).asTypeOf(new SplitCmdIO)), false.B)
    }
    io.isMatch := Mux(io.mpSearchReq.fire, conflictBitMap.asUInt.orR, false.B)
    io.isMatchData := Mux(onFireConflict, io.mpMissReq.bits.data, Mux1H(conflictBitMap.asUInt, WCBRegs).req.data)
    when(io.isMatch) { WCBRegs(Mux(onFireConflict, WCBIdx, PriorityEncoder(conflictBitMap.asUInt))).isMatch := true.B }

    // Clear conflict
    io.clearConflict.ready := true.B 
    when(io.clearConflict.fire){
        WCBRegs(conflictIdx).isMatch := false.B 
        when(io.clearConflict.bits.overWrite) { WCBRegs(conflictIdx).req.data := io.clearConflict.bits.wdata}
    }

    val addr = Cat(WCBRegs(WCBPtr).req.tag, WCBRegs(WCBPtr).req.set,WCBRegs(WCBPtr).req.bank, 0.U(OffsetBits.W)) >> 3
    val rankStart = BUNDLE_PARAM.BG_WIDTH+BUNDLE_PARAM.BANK_WIDTH+BUNDLE_PARAM.COL_WIDTH+BUNDLE_PARAM.ROW_WIDTH
    val rowStart = BUNDLE_PARAM.BG_WIDTH+BUNDLE_PARAM.BANK_WIDTH+BUNDLE_PARAM.COL_WIDTH
    val col3Start = 3+BUNDLE_PARAM.BG_WIDTH+BUNDLE_PARAM.BANK_WIDTH
    
    val RANK_LSB = rankStart
    val RANK_MSB = RANK_LSB + BUNDLE_PARAM.RANK_WIDTH - 1
    val rank = addr(RANK_MSB, RANK_LSB)
    
    io.asReq.valid := !isEmpty && (!io.isMatch)
    io.asData.valid := io.asReq.valid
    when(io.asReq.valid) {
        io.asReq.bits.rank := rank
        io.asReq.bits.bg := addr(3+BUNDLE_PARAM.BG_WIDTH-1,3)
        io.asReq.bits.bank := addr(col3Start-1,3+BUNDLE_PARAM.BG_WIDTH)
        io.asReq.bits.row := addr(rankStart-1,rowStart)
        io.asReq.bits.col := Cat(addr(rowStart-1,col3Start), addr(2,0))
        io.asReq.bits.pri := DontCare
        io.asReq.bits.token := DontCare

        io.asData.bits.wstrb := "hffff_ffff_ffff_ffff".U
        io.asData.bits.wdata := WCBRegs(WCBPtr).req.data
    }.otherwise {
        io.asReq.bits := 0.U.asTypeOf(new SplitCmdIO)
        io.asData.bits := 0.U.asTypeOf(new WrDataIO)
    }

    io.mpMissReq.ready := !isFull
}
