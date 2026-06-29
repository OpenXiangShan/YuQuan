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


class WriteReqIO(Token_Width : Int = BUNDLE_PARAM.TOKEN_WIDTH) extends Bundle{
    val cmd   = new CMDIO(Token_Width)
    val data  = new WrDataIO()
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
class txn_fifo_adr extends fifo_adr(1)
// class cmd_fifo_adr extends fifo_adr(3)
// class fifolinkRG   extends fifo_adr(3)
class ReflinkRG (width : Int)extends Bundle{
    val slave         = new ReflinkRG_in
    val master        = new ReflinkRG_out(width)
}
class ReflinkRG_in extends Bundle{
    val flowReq    = Flipped(Bool())
    val releaseReq = Flipped(Bool())
    val preIss     = Flipped(Bool())
}
class ReflinkRG_out (width : Int) extends Bundle{
    val flowAck    = Vec(width,Bool())
}
class ReflinkArb extends Bundle{
    // val refing     = Bool()
    val refIss     = Bool()
    val zqIss      = Bool()
    val preIss     = Bool()
}
class RefTime extends  Bundle{
    val tZQCS      = UInt(BUNDLE_PARAM.McParamWidth.W)
    val tZQINTVL   = UInt(BUNDLE_PARAM.tZQINTVL_Witdh.W)
    val tRP        = UInt(BUNDLE_PARAM.McParamWidth.W)
    val tREFI      = UInt(BUNDLE_PARAM.McParamWidth.W)
    val tRFC       = UInt(BUNDLE_PARAM.McParamWidth.W)
    val RefreshMode= UInt(2.W)
}
class TClinkRG extends Bundle{
    val tRRD_L_OK    = Flipped(Bool())
    val tRRD_S_OK    = Flipped(Bool())
    val tFAW_OK      = Flipped(Bool())
    val tRAS_OK      = Flipped(Vec((1<<BUNDLE_PARAM.BGBITS),Bool()))
    val tRCD_OK      = Flipped(Vec((1<<BUNDLE_PARAM.BGBITS),Bool()))
    val tRP_OK       = Flipped(Vec((1<<BUNDLE_PARAM.BGBITS),Bool()))
    val tCCD_L_OK    = Flipped(Bool())
    val tCCD_S_OK    = Flipped(Bool())
    val tWR_OK       = Flipped(Vec((1<<BUNDLE_PARAM.BGBITS),Bool()))
    val tRTW_OK      = Flipped(Bool())
    val tRTP_OK      = Flipped(Vec((1<<BUNDLE_PARAM.BGBITS),Bool()))
    val tWTR_L_OK    = Flipped(Bool())
    val tWTR_S_OK    = Flipped(Bool())

    val tW2WDR_OK    = Flipped(Bool())
    val tW2RDR_OK    = Flipped(Bool())
    val tR2RDR_OK    = Flipped(Bool())
    val tR2WDR_OK    = Flipped(Bool()) 
}
class arblinkRG extends Bundle{
    val in          = new arblinkRG_in
    val out         = new arblinkRG_out
}
class arblinkRG_out extends Bundle{
    val Cas_PopReq      = UInt(1.W)
    val Act_PopReq      = UInt(1.W)
    val Pre_PopReq      = UInt(1.W)
    val preReq          = Bool()
    val actReq          = Bool()
    val readReq         = Bool()
    val writeReq        = Bool()
    val actAdr          = new adr()
    val preAdr          = new adr()
    val casAdr          = new adr()
}
class arblinkRG_in extends Bundle{
    val Pre_PopOK      =  Flipped(Bool())
    val Cas_PopOK      =  Flipped(Bool())
    val Act_PopOK      =  Flipped(Bool())
    // val won            =  Flipped(new WonCmd2TC) 
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
// class arb2TC      extends Bundle{
//     val  wonwrite       = Bool()
//     val  wonread        = Bool()
//     val  wonact         = Bool()
//     val  wonpre         = Bool()
//     val  wonCasBG       = UInt(BUNDLE_PARAM.BGBITS.W)
//     val  wonCasBA       = UInt(BUNDLE_PARAM.BABITS.W)
//     val  wonPreBG       = UInt(BUNDLE_PARAM.BGBITS.W)
//     val  wonPreBA       = UInt(BUNDLE_PARAM.BABITS.W)
//     val  wonActBG       = UInt(BUNDLE_PARAM.BGBITS.W)
//     val  wonActBA       = UInt(BUNDLE_PARAM.BABITS.W)

// }
class RGlinkTC  extends Bundle{  // reload TC timer counter
    val act_req         = Bool()
    val act_bg          = UInt(BUNDLE_PARAM.BGBITS.W)
    val act_ba          = UInt(BUNDLE_PARAM.BABITS.W)
    val act_rank        = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
    val write_req       = Bool()
    val read_req        = Bool()
    val cas_bg          = UInt(BUNDLE_PARAM.BGBITS.W)
    val cas_ba          = UInt(BUNDLE_PARAM.BABITS.W)
    val cas_rank        = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
    val pre_req         = Bool()
    val pre_bg          = UInt(BUNDLE_PARAM.BGBITS.W)
    val pre_ba          = UInt(BUNDLE_PARAM.BABITS.W)   
    val pre_rank        = UInt(BUNDLE_PARAM.RANK_WIDTH.W)    
    // val bank            = UInt(BUNDLE_PARAM.BABITS.W)
}
class arb2RG extends Bundle{
    val    arbout       = Flipped(Vec(1<<(BUNDLE_PARAM.RANK_WIDTH+BUNDLE_PARAM.BGBITS),new arblinkRG_in))
    val    arbin        = Flipped(Vec(1<<(BUNDLE_PARAM.RANK_WIDTH+BUNDLE_PARAM.BGBITS),new arblinkRG_out))
}
class arb2widthMatch extends Bundle{
       val      actReq        =  Bool()
       val      winRankAT     =  UInt(BUNDLE_PARAM.RANK_WIDTH.W)
       val      winGroupA     =  UInt(BUNDLE_PARAM.BGBITS.W) 
       val      winBankAT     =  UInt(BUNDLE_PARAM.BABITS.W)
       val      winROW        =  UInt(BUNDLE_PARAM.ABITS.W)
       val      winROWP       =  UInt(BUNDLE_PARAM.ABITS.W)
       val      writeReq      =  Bool()
       val      readReq       =  Bool()
       val      rankCas       =  UInt(BUNDLE_PARAM.RANK_WIDTH.W)
       val      readToken     =  UInt(BUNDLE_PARAM.TOKENBITS.W)
       val      groupCas      =  UInt(BUNDLE_PARAM.BGBITS.W)
       val      bankCas       =  UInt(BUNDLE_PARAM.BABITS.W)
       val      winCOL        =  UInt(BUNDLE_PARAM.COLBITS.W)
       val      preReq        =  Bool()
       val      rankP         =  UInt(BUNDLE_PARAM.RANK_WIDTH.W)
       val      winGroupP     =  UInt(BUNDLE_PARAM.BGBITS.W)
       val      winBankP      =  UInt(BUNDLE_PARAM.BABITS.W)
       val      arb2WM_refInt =  new ReflinkArb
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
class mig2dfi extends Bundle {
  // control interface
  val dfi_address = Output(UInt((BUNDLE_PARAM.ABITS << 1).W))
  val dfi_bank    = Output(UInt((BUNDLE_PARAM.BABITS << 1).W))
  val dfi_ras_n   = Output(UInt((BUNDLE_PARAM.RasNWidth<<1).W))
  val dfi_cas_n   = Output(UInt((BUNDLE_PARAM.CasNWidth<<1).W))
  val dfi_we_n    = Output(UInt((BUNDLE_PARAM.WeNWidth<<1).W))
  val dfi_cs_n    = Vec(2,Vec(2,Bool()))
  val dfi_act_n   = Output(UInt((BUNDLE_PARAM.ActNWidth<<1).W))
  val dfi_bg      = Output(UInt((BUNDLE_PARAM.BGBITS << 1).W))
  val dfi_cke     = Vec(2,Vec(2,Bool()))
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
// AS2SCG interface
class AS2SCG extends Bundle {
  val cmdRdy   = Output(Bool())
  val cmdValid = Input(Bool())
  val cmd      = Input(Bool()) //读1/写0命令
  val bank     = Input(UInt(BUNDLE_PARAM.BABITS.W))
  val col      = Input(UInt(BUNDLE_PARAM.COLBITS.W))
  val group    = Input(UInt(BUNDLE_PARAM.BGBITS.W))
  val rank     = Input(Bool())
  val row      = Input(UInt(BUNDLE_PARAM.ABITS.W))
  val token    = Input(UInt(BUNDLE_PARAM.TOKENBITS.W))
  val priority = Input(Bool())
  val wrData   = Input(UInt(BUNDLE_PARAM.DATABITS.W))
  val dataMask = Input(UInt((BUNDLE_PARAM.DATABITS >> 3).W))
}
//read data 2 AS
class R2AS extends Bundle {
  val rdData = Output(UInt(BUNDLE_PARAM.DATA_WIDTH.W))
  val token  = Output(UInt(BUNDLE_PARAM.TOKENBITS.W))
}
class group2CD extends Bundle{
    val preReq      =  Input(Bool())
    val refReq      =  Input(Bool())
    val zqReq       =  Input(Bool())
    val writeReq    =  Input(Bool())
    val readReq     =  Input(Bool())
    val actReq      =  Input(Bool())
    val row         =  Input(UInt(BUNDLE_PARAM.ABITS.W))
    val col         =  Input(UInt(BUNDLE_PARAM.COLBITS.W))
    val rank        =  Input(UInt(BUNDLE_PARAM.RKBITS.W))
    val bank        =  Input(UInt(BUNDLE_PARAM.BABITS.W))
    val BG          =  Input(UInt(BUNDLE_PARAM.BGBITS.W))

}
class TC2REF  extends Bundle{
    val  RTP_OK        = Bool()
    val  RAS_OK        = Bool()
    val  WTP_OK        = Bool() 
}
class WonCmd2TC extends Bundle{
    val  write_phase0    = Bool()
    val  write_phase1    = Bool()
    val  read_phase0     = Bool()
    val  read_phase1     = Bool()
    val  pre_phase0      = Bool()
    val  pre_phase1      = Bool()
    val  act_phase0      = Bool()
    val  act_phase1      = Bool()
    val  dfi_phase0_rank = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
    val  dfi_phase1_rank = UInt(BUNDLE_PARAM.RANK_WIDTH.W)
    val  dfi_phase0_bg   = UInt(BUNDLE_PARAM.BGBITS.W) 
    val  dfi_phase0_ba   = UInt(BUNDLE_PARAM.BABITS.W)
    val  dfi_phase1_bg   = UInt(BUNDLE_PARAM.BGBITS.W) 
    val  dfi_phase1_ba   = UInt(BUNDLE_PARAM.BABITS.W)

}