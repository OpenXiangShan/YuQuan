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
import chisel3.SpecifiedDirection.Flip

class CMDIO(Token_Width :  Int = BUNDLE_PARAM.TOKEN_WIDTH) extends Bundle{
    val addr   = UInt(BUNDLE_PARAM.ADDR_WIDTH.W)
    val token  = UInt(Token_Width.W)
    val pri    = UInt(BUNDLE_PARAM.PRI_WIDTH.W)   
}
class SplitCmdIO(Token_Width :  Int = BUNDLE_PARAM.TOKEN_WIDTH) extends Bundle{
    val rank = Output(UInt(BUNDLE_PARAM.RANK_WIDTH.W))
    val bg   = Output(UInt(BUNDLE_PARAM.BG_WIDTH.W))
    val bank = Output(UInt(BUNDLE_PARAM.BANK_WIDTH.W))
    val row  = Output(UInt(BUNDLE_PARAM.ROW_WIDTH.W))
    val col  = Output(UInt(BUNDLE_PARAM.COL_WIDTH.W))

    val pri  = Output(UInt(BUNDLE_PARAM.PRI_WIDTH.W))
    val token= Output(UInt(Token_Width.W))
}

class As2ScgCmdIO(width : Int ) extends Bundle{
    val ADR  = new fifo_adr(width)
    val pri  = UInt(BUNDLE_PARAM.PRI_WIDTH.W)
}
class RdDataIO(Token_Width :  Int = BUNDLE_PARAM.TOKEN_WIDTH) extends Bundle{
    val rdata  = UInt(BUNDLE_PARAM.DATA_WIDTH.W)
    val rtoken = UInt(Token_Width.W)
}

class WrDataIO extends Bundle{
    val wdata  = UInt(BUNDLE_PARAM.DATA_WIDTH.W)
    val wstrb  = UInt(BUNDLE_PARAM.STRB_WIDTH.W)
}


class adr extends Bundle{
    val rank     = UInt(BUNDLE_PARAM.RKBITS.W)
    val group    = UInt(BUNDLE_PARAM.BGBITS.W)
    val bank     = UInt(BUNDLE_PARAM.BABITS.W)
    val row      = UInt(BUNDLE_PARAM.ROW_WIDTH.W)
    val col      = UInt(BUNDLE_PARAM.COLBITS.W)
    val cmdToken = UInt(BUNDLE_PARAM.TOKENBITS.W)
}
class fifo_adr (width : Int )extends Bundle{
    val cmdtype  = UInt(width.W)
    val adr      = new adr
}
class RefTime extends  Bundle{
    val tZQCS      = UInt(BUNDLE_PARAM.McParamWidth.W)
    val tZQINTVL   = UInt(BUNDLE_PARAM.tZQINTVL_Witdh.W)
    val tRP        = UInt(BUNDLE_PARAM.McParamWidth.W)
    val tREFI      = UInt(BUNDLE_PARAM.McParamWidth.W)
    val tRFC       = UInt(BUNDLE_PARAM.McParamWidth.W)
    val RefreshMode= UInt(2.W)
}
class  TCtime extends Bundle{
    val  tRRD_S         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tRRD_L         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tFAW           = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tRAS           = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tRCD           = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tRP            = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tCCD_S         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tCCD_L         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tWR            = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tWTR_S         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tWTR_L         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tRTW           = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tRTP           = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  AL             = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  RL             = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  WL             = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  BL             = UInt(BUNDLE_PARAM.McParamWidth.W)
    //across ranks parameters
    val  tW2WDR         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tW2RDR         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tR2RDR         = UInt(BUNDLE_PARAM.McParamWidth.W)
    val  tR2WDR         = UInt(BUNDLE_PARAM.McParamWidth.W)       
}
// dfi interface
class dfiBundle extends Bundle {
  val dfictrl     = new dfiCtl
  val dfiwrdata   = new dfiWrData
  val dfirddata   = new dfiRdData
  val dfiupdate   = new dfiUpdate
  val dfistatus   = new dfiStatus
  val dfitraining = new dfiTraining
  val dfilp       = new dfiLP
}
class dfiCtl extends Bundle {
  // control interface
  val dfi_address = Vec(2,UInt(BUNDLE_PARAM.ABITS.W))
  val dfi_bank    = Vec(2,UInt(BUNDLE_PARAM.BABITS.W))
  val dfi_ras_n   = Vec(2,Bool())
  val dfi_cas_n   = Vec(2,Bool())
  val dfi_we_n    = Vec(2,Bool())
  val dfi_cs_n    = Vec(2,Vec(2,Bool()))
  val dfi_act_n   = Vec(2,Bool())
  val dfi_bg      = Vec(2,UInt(BUNDLE_PARAM.BGBITS.W))
  val dfi_cid     = Output(Bool())
  val dfi_cke     = Vec(2,Vec(2,Bool()))
  val dfi_odt     = Vec(2,Vec(2,Bool()))
  val dfi_reset_n = Vec(2,Vec(2,Bool()))
}
class dfiWrData extends Bundle {
  // Write Data interface
  val dfi_wrdata_en  = Output(UInt(BUNDLE_PARAM.WrDataEnWidth.W))
  val dfi_wdata      = Output(UInt(BUNDLE_PARAM.DATABITS.W))
  val dfi_wdata_cs_n = Output(UInt(BUNDLE_PARAM.WrDataCsNWidth.W))
  val dfi_wdata_mask = Output(UInt((BUNDLE_PARAM.DATABITS >> 3).W))
}
class dfiRdData extends Bundle {
  // Read Data interface
  val dfi_rddata_en    = Output(UInt(BUNDLE_PARAM.RdDataEnWidth.W))
  val dfi_rddata       = Input(UInt(BUNDLE_PARAM.DATABITS.W))
  val dfi_rddata_cs_n  = Output(UInt(BUNDLE_PARAM.RdDataCsNWidth.W))
  val dfi_rddata_valid = Input(UInt(BUNDLE_PARAM.RdDataVldWidth.W))
  val dfi_rddata_dbi_n = Input(UInt(BUNDLE_PARAM.RdDataDbiWidth.W))
}
class dfiUpdate extends Bundle {
  //  Update Interface
  val dfi_ctrlupd_req = Output(Bool())
  val dfi_ctrlupd_ack = Input(Bool())
  val dfi_phyupd_req  = Input(Bool())
  val dfi_phyupd_type = Input(UInt(2.W))
  val dfi_phyupd_ack  = Output(Bool())
}
class dfiStatus extends Bundle {
  //    Status Interface
  val dfi_data_byte_disable = Output(UInt(32.W))
  val dfi_dram_clk_disable  = Output(UInt(2.W))
  val dfi_freq_ratio        = Output(UInt(2.W))
  val dfi_init_start        = Output(Bool())
  val dfi_init_complete     = Input(Bool())
  val dfi_parity_in         = Output(Bool())
  val dfi_alert_n           = Input(UInt(2.W))
}
class dfiTraining extends Bundle {
  //  Training Interface
  val dfi_rdlvl_req           = Input(Bool())
  val dfi_phy_rdlvl_cs_n      = Input(UInt(2.W))
  val dfi_rdlvl_en            = Output(Bool())
  val dfi_rdlvl_resp          = Input(UInt(4.W))
  val dfi_rdlvl_gate_req      = Input(Bool())
  val dfi_phy_rdlvl_gate_cs_n = Input(UInt(2.W))
  val dfi_rdlvl_gate_en       = Output(Bool())
  val dfi_wrlvl_req           = Input(UInt(4.W))
  val dfi_phy_wrlvl_cs_n      = Input(UInt(2.W))
  val dfi_wrlvl_en            = Output(Bool())
  val dfi_wrlvl_strobe        = Output(Bool())
  val dfi_wrlvl_resp          = Input(UInt(4.W))
  val dfi_lvl_pattern         = Output(UInt(8.W))
  val dfi_lvl_periodic        = Output(Bool())
  val dfi_phylvl_req_cs_n     = Input(Bool())
  val dfi_phylvl_ack_cs_n     = Output(UInt(2.W))
}
class dfiLP extends Bundle {
  //  Low Power Control Interface
  val dfi_lp_ctrl_req = Output(Bool())
  val dfi_lp_data_req = Output(Bool())
  val dfi_lp_wakeup   = Output(UInt(4.W))
  val dfi_lp_ack      = Input(Bool())
}
//read data 2 AS
class R2AS extends Bundle {
  val rdData = Output(UInt(BUNDLE_PARAM.DATA_WIDTH.W))
  val token  = Output(UInt(BUNDLE_PARAM.TOKENBITS.W))
}
