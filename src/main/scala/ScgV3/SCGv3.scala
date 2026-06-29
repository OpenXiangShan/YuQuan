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
import chisel3.SpecifiedDirection.Flip
import APB.APBSlvtop
class  globalWdataIO (WriteTokenWidth : Int = BUNDLE_PARAM.TOKEN_WIDTH)extends  Bundle{
                    val wdata  = Flipped(UInt(BUNDLE_PARAM.DATA_WIDTH.W))
                    val wstrb  = Flipped(UInt((BUNDLE_PARAM.DATA_WIDTH>>3).W))
                    val wtoken = UInt(WriteTokenWidth.W)
                    val valid  = Flipped(Bool())
                    val ready  = Bool()
}

class SCG_V3 extends Module {
  val all_bank_num = (1<<(BG_WIDTH+RANK_WIDTH))<<BANK_WIDTH
  val WriteTokenWidth =  log2Ceil((BUNDLE_PARAM.WrSchedulerQueueDepth << (BUNDLE_PARAM.RANK_WIDTH + BUNDLE_PARAM.BG_WIDTH)) + (1<<(BUNDLE_PARAM.RANK_WIDTH + BUNDLE_PARAM.BG_WIDTH + BUNDLE_PARAM.BANK_WIDTH)))
  val io = IO(new Bundle {
    val dfi      = new dfiBundle
    val AsScgCmd = Vec(all_bank_num, Flipped(Decoupled(new fifo_adr(1))))
    val globalWdata = new globalWdataIO(WriteTokenWidth)
    val ScgAsRddata = Decoupled(new RdDataIO(TOKENBITS))
    val scgregio    = Flipped(new SCGRegIO)
    val SchedulerQueueIsEmpty = Flipped(Bool())
    val calDone     = Flipped(Bool())
  })
  dontTouch(io)
  io.dfi := DontCare
  io.scgregio := DontCare

//==============================================================================================
//                           Module Instantiation
//==============================================================================================
  val rd_data_buf = Module(new RdDataBuffer).io
  val cmdGen      = VecInit(Seq.fill(all_bank_num)(Module(new CommandGen).io))
  val refresh     = Module(new Refresh).io 
  val timingArb   = Module(new TimingArb).io
  val phaseCtrl   = Module(new DFIPhaseCtrl(WriteTokenWidth)).io
  val dfiAdapter  = Module(new DFIAdapter).io
  val ddr_init    = Module(new ddr4_init(BUNDLE_PARAM.BGBITS,BUNDLE_PARAM.BABITS,BUNDLE_PARAM.ABITS)).io
//==============================================================================================
//                         feed sub modules' input ports
//==============================================================================================
  val dfiHasWrite = phaseCtrl.dfi_write_ph.reduce(_|_)
  val dfiHasRead  = phaseCtrl.dfi_read_ph.reduce(_|_)
  io.globalWdata.ready := dfiHasWrite
  io.globalWdata.wtoken := phaseCtrl.write_token


  // cmdGen
  for (i <- 0 until cmdGen.length) {
    cmdGen(i).calDone := io.scgregio.gen & io.calDone 
    cmdGen(i).request :<>= io.AsScgCmd(i)
    cmdGen(i).requestID :<>= i.U.asTypeOf(cmdGen(i).requestID)
    cmdGen(i).refCtrl :<>= refresh.refCmdGen(i)
  }

  // timingArb
  timingArb.refresh :<>= refresh.refArb
  timingArb.parameters.tRRD_S := io.scgregio.tRRDS
  timingArb.parameters.tRRD_L := io.scgregio.tRRDL
  timingArb.parameters.tFAW   := io.scgregio.tFAW
  timingArb.parameters.tRAS   := io.scgregio.tRAS
  timingArb.parameters.tRCD   := io.scgregio.tRCD
  timingArb.parameters.tRP    := io.scgregio.tRP
  timingArb.parameters.tCCD_S := io.scgregio.tCCDS
  timingArb.parameters.tCCD_L := io.scgregio.tCCDL
  timingArb.parameters.tWR    := io.scgregio.tWR
  timingArb.parameters.tWTR_S := io.scgregio.tWTRS
  timingArb.parameters.tWTR_L := io.scgregio.tWTRL
  timingArb.parameters.tRTW   := io.scgregio.tRTW
  timingArb.parameters.tRTP   := io.scgregio.tRTP
  timingArb.parameters.AL     := io.scgregio.AL
  timingArb.parameters.RL     := io.scgregio.RL
  timingArb.parameters.WL     := io.scgregio.WL
  timingArb.parameters.BL     := io.scgregio.BL
  timingArb.parameters.tR2RDR := io.scgregio.r2rdr
  timingArb.parameters.tR2WDR := io.scgregio.r2wdr
  timingArb.parameters.tW2RDR := io.scgregio.w2rdr
  timingArb.parameters.tW2WDR := io.scgregio.w2wdr
  for (i <- 0 until cmdGen.length) {
    timingArb.cmdGen(i) :<>= cmdGen(i).arb
  }

  // refresh
  refresh.CalDone             := io.scgregio.gen & io.calDone
  refresh.timeParam.tZQCS     := io.scgregio.tZQCS
  refresh.timeParam.tZQINTVL  := io.scgregio.tZQINTVL
  refresh.timeParam.tRP       := io.scgregio.tRP
  refresh.timeParam.tREFI     := io.scgregio.tREFI
  refresh.timeParam.tRFC      := io.scgregio.tRFC
  refresh.timeParam.RefreshMode:= io.scgregio.scgMode
  refresh.SchedulerQueueIsEmpty:= io.SchedulerQueueIsEmpty

  //phaseCtrl
  phaseCtrl.winCmd            := timingArb.phaseCtrl
  phaseCtrl.Mrs_Req           := ddr_init.MrsReq
  phaseCtrl.Mrs_rank          := ddr_init.MrsRank
  phaseCtrl.Mrs_BG            := ddr_init.MrsBG
  phaseCtrl.Mrs_BA            := ddr_init.MrsBA
  phaseCtrl.Mrs_ADDR          := ddr_init.MrsAddr
  phaseCtrl.Zqcl_req          := ddr_init.zqlreq
  phaseCtrl.cke               := ddr_init.init_cke
  phaseCtrl.init_process      := ddr_init.init_in_progress
  phaseCtrl.dram_rst_n        := ddr_init.dram_rst_n

  // ddr_init
  ddr_init.apbDone               := io.calDone
  ddr_init.mrs0                  := io.scgregio.mrs0
  ddr_init.mrs1                  := io.scgregio.mrs1
  ddr_init.mrs2                  := io.scgregio.mrs2
  ddr_init.mrs3                  := io.scgregio.mrs3
  ddr_init.mrs4                  := io.scgregio.mrs4
  ddr_init.mrs5                  := io.scgregio.mrs5
  ddr_init.mrs6                  := io.scgregio.mrs6
  ddr_init.mrs_to_other          := io.scgregio.mrs2other
  ddr_init.mrs_to_mrs            := io.scgregio.mrs2mrs
  ddr_init.dram_rstn             := io.scgregio.dramRstn
  ddr_init.phy_dfi_init_complete := io.dfi.dfistatus.dfi_init_complete
  ddr_init.pre_cke               := io.scgregio.preCke
  ddr_init.post_cke              := io.scgregio.postCke
  ddr_init.sync_gear             := io.scgregio.syncGear
  ddr_init.cmd_gear              := io.scgregio.cmdGear
  ddr_init.gear_setup            := io.scgregio.gearSetup
  ddr_init.gear_hold             := io.scgregio.gearHold
  ddr_init.geardown_mode         := io.scgregio.geardownMode
  ddr_init.block_tgeardown       := io.scgregio.blkTGeardown
  ddr_init.zqinit                := io.scgregio.zqinit
  ddr_init.end_init_ddr          <> io.scgregio.ddrInitEnd
  // dfiAdapter
  dfiAdapter.dfi_ctrl_in         := phaseCtrl.adpter
  dfiAdapter.dfi_wrdata.wdata := io.globalWdata.wdata
  dfiAdapter.dfi_wrdata.wstrb := io.globalWdata.wstrb
  dfiAdapter.dfi_write_ph := phaseCtrl.dfi_write_ph
  dfiAdapter.dfi_read_ph  := phaseCtrl.dfi_read_ph
  dfiAdapter.dfi_rddata_in <> io.dfi.dfirddata
  dfiAdapter.dfi_parameter_mode := io.scgregio.dfiMode
  dfiAdapter.tphy_wrlat   := io.scgregio.tphyWrlat
  dfiAdapter.tphy_wrcslat := io.scgregio.tphyWrcslat
  dfiAdapter.tphy_wrdata  := io.scgregio.tphyWrdata
  dfiAdapter.trddata_en   := io.scgregio.trddataEn
  dfiAdapter.tphy_rdcslat := io.scgregio.tphyRdcslat
  dfiAdapter.tphy_rdlat   := io.scgregio.tphyRdlat
  dfiAdapter.wr_odt_delay    := io.scgregio.wrOdtDelay
  dfiAdapter.wr_odt_hold     := io.scgregio.wrOdtHold
  dfiAdapter.rd_odt_delay    := io.scgregio.rdOdtDelay
  dfiAdapter.rd_odt_hold     := io.scgregio.rdOdtHold


  // rd_data
  rd_data_buf.tokenEn := dfiHasRead
  rd_data_buf.tokenIn := phaseCtrl.read_token
  rd_data_buf.dataEn  := dfiAdapter.dfi_rddata_ready
  rd_data_buf.dataIn  := dfiAdapter.dfi_rddata_out
//==============================================================================================
//                         feed top module's output ports
//==============================================================================================
  io.ScgAsRddata.valid := rd_data_buf.dataOut.valid
  io.ScgAsRddata.bits.rdata := rd_data_buf.dataOut.bits.data
  io.ScgAsRddata.bits.rtoken := rd_data_buf.dataOut.bits.token
  rd_data_buf.dataOut.ready := io.ScgAsRddata.ready

  io.dfi.dfiwrdata <> dfiAdapter.dfi_wrdata_ch_out
  io.dfi.dfictrl <> dfiAdapter.dfi_ctrl_out

  io.scgregio.ddrInitEnd := ddr_init.end_init_ddr
}