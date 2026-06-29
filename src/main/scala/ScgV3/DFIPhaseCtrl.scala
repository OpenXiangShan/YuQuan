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
import chisel3.SpecifiedDirection.Flip
import java.awt.BufferCapabilities.FlipContents

class PhaseAdapter extends Bundle {
    val dfiAdr  = Vec(2,UInt(BUNDLE_PARAM.ABITS.W))
    val dfiBa   = Vec(2,UInt(BUNDLE_PARAM.BABITS.W))
    val dfiRasN = Vec(2,UInt())
    val dfiCasN = Vec(2,UInt())
    val dfiWeN  = Vec(2,UInt())
    val dfiCsN  = Vec(2,UInt())
    val dfiActN = Vec(2,UInt())
    val dfiBg   = Vec(2,UInt(BUNDLE_PARAM.BGBITS.W))
    val dfiCke  = Vec(2,UInt(BUNDLE_PARAM.CKEBITS.W))
    val dfiResetN = Vec(2,UInt())
}


class  DFIPhaseCtrl (WriteTokenWidth : Int = BUNDLE_PARAM.TOKEN_WIDTH)extends Module {
    val  io = IO(new Bundle{
        val winCmd     = Flipped(new TimingDFIPhase)

        val Mrs_Req    = Flipped(Bool())
        val Mrs_BG     = Flipped(UInt(BUNDLE_PARAM.BGBITS.W))
        val Mrs_BA     = Flipped(UInt(BUNDLE_PARAM.BABITS.W))
        val Mrs_ADDR   = Flipped(UInt(BUNDLE_PARAM.ABITS.W))
        val Mrs_rank   = Flipped(UInt(BUNDLE_PARAM.RANK_WIDTH.W))
        val Zqcl_req   = Flipped(Bool()) 
        val cke        = Flipped(Bool())
        val init_process = Flipped(Bool())
        val dram_rst_n   = Flipped(Bool())

        val dfi_write_ph = Vec(2, Bool())
        val dfi_read_ph  = Vec(2, Bool())
        val write_token  = UInt(WriteTokenWidth.W) 
        val read_token   = UInt(BUNDLE_PARAM.TOKENBITS.W)

        val adpter    = new dfiCtl()
    })
    dontTouch(io)
    //CMD
    val  WRITE            = "b01100".U(5.W)
    val  READ             = "b01101".U(5.W)
    val  PRECHARGE        = "b01010".U(5.W)
    val  ACT              = "b00000".U(5.W)
    val  REF              = "b01001".U(5.W)
    val  ZQ               = "b01110".U(5.W)
    val  NOP              = "b11111".U(5.W)
    val  MRS              = "b01000".U(5.W)

    val                   RANK0  = false.B
    val                   RANK1  = true.B
    val                   PHASE0 = 0.U
    val                   PHASE1 = 1.U
    io.adpter.dfi_reset_n(0)(0) := io.dram_rst_n
    io.adpter.dfi_reset_n(0)(1) := io.dram_rst_n
    io.adpter.dfi_reset_n(1)(0) := io.dram_rst_n
    io.adpter.dfi_reset_n(1)(1) := io.dram_rst_n
    io.adpter.dfi_cke(0)(0)     := Mux(io.init_process, io.cke, true.B)
    io.adpter.dfi_cke(0)(1)     := Mux(io.init_process, io.cke, true.B)
    io.adpter.dfi_cke(1)(0)     := Mux(io.init_process, io.cke, true.B)
    io.adpter.dfi_cke(1)(1)     := Mux(io.init_process, io.cke, true.B)
    io.adpter.dfi_cid            := DontCare
    io.adpter.dfi_odt           := DontCare
    // 新增函数：设置DFI命令信号
    def dfiCmdSet(Clear: Bool,BackOpt : Bool ,IsAct: Bool,rank: Bool , phase: UInt ,cmd:  UInt ,ba: UInt ,bg: UInt ,addr: UInt ): Unit = {
        io.adpter.dfi_cs_n(0)(0)         := Mux(Clear,NOP(4),Mux(BackOpt,cmd(4),Mux(rank,NOP(4),cmd(4)))) //rank0 phase0
        io.adpter.dfi_cs_n(0)(1)         := Mux(Clear,NOP(4),Mux(BackOpt,cmd(4),Mux(rank,cmd(4),NOP(4)))) //rank1 phase0
        io.adpter.dfi_cs_n(1)(0)         := NOP(4)                                                        //rank0 phase1
        io.adpter.dfi_cs_n(1)(1)         := NOP(4)                                                        //rank1 phase1
        io.adpter.dfi_act_n(0)           := Mux(Clear,NOP(3),cmd(3))
        io.adpter.dfi_act_n(1)           := NOP(3)
        io.adpter.dfi_ras_n(0)           := Mux(Clear,NOP(2),Mux(IsAct,addr(16),cmd(2)))
        io.adpter.dfi_ras_n(1)           := NOP(2)
        io.adpter.dfi_cas_n(0)           := Mux(Clear,NOP(1),Mux(IsAct,addr(15),cmd(1)))
        io.adpter.dfi_cas_n(1)           := NOP(1)
        io.adpter.dfi_we_n(0)            := Mux(Clear,NOP(0),Mux(IsAct,addr(14),cmd(0)))
        io.adpter.dfi_we_n(1)            := NOP(0)
        io.adpter.dfi_bank(0)             := Mux(Clear,0.U,ba)
        io.adpter.dfi_bank(1)             := 0.U
        io.adpter.dfi_bg(0)             := Mux(Clear,0.U,bg)
        io.adpter.dfi_bg(1)             := 0.U
        io.adpter.dfi_address(0)            := Mux(Clear,0.U,addr)
        io.adpter.dfi_address(1)            := 0.U
    }

    val hasRefReqs = io.winCmd.refIss | io.winCmd.preIss | io.winCmd.zqIss
    val hasnormalReqs = io.winCmd.actReq | io.winCmd.casReq | io.winCmd.preReq
    val actCount  = RegInit(0.U(32.W))//统计MC行为,忽略背景操作
    val WrCount   = RegInit(0.U(32.W))
    val RdCount   = RegInit(0.U(32.W))
    val PreCount  = RegInit(0.U(32.W))
    dontTouch(actCount)
    dontTouch(WrCount)
    dontTouch(RdCount)
    dontTouch(PreCount)
    actCount := actCount + io.winCmd.actReq
    WrCount  := WrCount  + (!io.winCmd.casRead & io.winCmd.casReq)
    RdCount  := RdCount  + (io.winCmd.casRead  & io.winCmd.casReq)
    PreCount := PreCount + io.winCmd.preReq
    assert(!(io.init_process & hasRefReqs))
    assert(!(io.init_process & hasnormalReqs))
    assert(!(hasRefReqs & hasnormalReqs))
    assert(PopCount(Cat(io.winCmd.refIss, io.winCmd.preIss, io.winCmd.zqIss)) <= 1.U)
    assert(PopCount(Cat(io.winCmd.actReq, io.winCmd.casReq, io.winCmd.preReq)) <= 2.U)

    dfiCmdSet(true.B,true.B,false.B,false.B,PHASE0,NOP,0.U,0.U,0.U)
        when(io.init_process){
            when(io.Mrs_Req  ){
                dfiCmdSet(false.B,true.B,false.B,io.Mrs_rank.asBool,PHASE0,MRS,io.Mrs_BA,io.Mrs_BG,io.Mrs_ADDR)
            }.elsewhen(io.Zqcl_req){
                dfiCmdSet(false.B,true.B,false.B,false.B,PHASE0,ZQ,io.Mrs_BA,io.Mrs_BG,io.Mrs_ADDR)
            }.otherwise{
                dfiCmdSet(false.B,true.B,false.B,false.B,PHASE0,NOP,io.Mrs_BA,io.Mrs_BG,io.Mrs_ADDR)
            }
        }.elsewhen(io.winCmd.preIss=== 1.U){
                dfiCmdSet(false.B,true.B,false.B,false.B,PHASE0,PRECHARGE,0.U,0.U,"b00_1000_0100_0000_0000".U)
        }.elsewhen(io.winCmd.refIss=== 1.U){
                dfiCmdSet(false.B,true.B,false.B,false.B,PHASE0,REF,0.U,0.U,"b00_0100_0000_0000_0000".U)
        }.elsewhen(io.winCmd.zqIss=== 1.U){
                dfiCmdSet(false.B,true.B,false.B,false.B,PHASE0,ZQ,0.U,0.U,"b01_1000_0000_0000_0000".U)
        }
            io.dfi_write_ph.foreach(_ := false.B)
            io.dfi_read_ph.foreach(_ := false.B)
        switch(Cat(io.winCmd.actReq,io.winCmd.casReq,io.winCmd.preReq)){
             is("b100".U){   //single phase
                 dfiCmdSet(false.B,false.B,true.B,io.winCmd.actRank.asBool,PHASE0,ACT,io.winCmd.actBa,io.winCmd.actBg,io.winCmd.actRow)
             }
             is("b010".U){
                 io.dfi_read_ph(0) := io.winCmd.casRead
                 io.dfi_write_ph(0):= !io.winCmd.casRead
                 dfiCmdSet(false.B,false.B,false.B,io.winCmd.casRank.asBool,PHASE0,Mux(io.winCmd.casRead,READ,WRITE),io.winCmd.casBa,io.winCmd.casBg,Cat(2.U(3.W), Mux(io.winCmd.casRead, READ(0), WRITE(0)), 4.U(4.W), io.winCmd.casCol))
             }
             is("b001".U){
                 dfiCmdSet(false.B,false.B,false.B,io.winCmd.preRank.asBool,PHASE0,PRECHARGE,io.winCmd.preBa,io.winCmd.preBg,"b00_1000_0000_0000_0000".U)
             }
         }

 
    io.write_token:= io.winCmd.casTok(WriteTokenWidth -1 ,0)
    io.read_token := io.winCmd.casTok

}