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




class osmc_axi_write[T <: AXI2UI_PARAMETER](
//AXI parameter define
    AXIW_PARAMETER   :   T
)extends Module{

class TOKEN_COUNTIO_W extends Bundle{
    val token_awen      =   Output(Bool())
    val token_awready   =   Output(Bool())
}
class AXI_WRITEIO extends Bundle{
    //AXI write address
    val axi_awio= new AXI_AWIO()
    //AXI write data
    val axi_wio = new AXI_WIO()
    //AXI write response    
    val axi_bio = new AXI_BIO()     
    //UI write
    val ui_awio = Decoupled(new CMDIO())
    val ui_wio  = Decoupled(new WrDataIO())     
    val ready_stall =   Input(Bool())
    //consis
    val wconsis =   Input(Bool())
    val consis_addr_io  = new CONS_ADDR_IO()
    val ui_wtcmd_counter = Output(UInt(32.W))
    val axi_wtcmd_counter = Output(UInt(32.W))
    //apb config done 
    val apb_config_done  = Flipped(Bool())
}

/*********************************************************************************************************************************************************/
//local parameter define
val FIFO_AWL1_PARAM = AXIW_PARAMETER.AXIWFIFO_PARAMETER.FIFOAWL1_PARAMETER
val FIFO_AWL2_PARAM = AXIW_PARAMETER.AXIWFIFO_PARAMETER.FIFOAWL2_PARAMETER
val FIFO_WL1_PARAM  = AXIW_PARAMETER.AXIWFIFO_PARAMETER.FIFOWL1_PARAMETER
val FIFO_WL2_PARAM  = AXIW_PARAMETER.AXIWFIFO_PARAMETER.FIFOWL2_PARAMETER
val FIFO_B_PARAM    = AXIW_PARAMETER.AXIWFIFO_PARAMETER.FIFOB_PARAMETER
val FIFO_BO_PARAM   = AXIW_PARAMETER.AXIWFIFO_PARAMETER.FIFOBO_PARAMETER
val CLIP_PAPAM = AXIW_PARAMETER
val UICMD_PARAM     = AXIW_PARAMETER
val UIDATA_PARAM    = AXIW_PARAMETER
val TABLE_PARAM     = AXIW_PARAMETER  

val AXI_BW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_BURSTW
val AXI_AW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_ADDRW
val AXI_LW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_LENW
val AXI_SW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_SIZEW
val AXI_QW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_QOSW
val AXI_IW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_IDW
val AXI_UW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_USERW
/*****************************************************************************************************************************************************/

//IO define
val io = IO(new AXI_WRITEIO())  

/*************************************************************** consistency ***********************************************************************/
val addr0  =   RegInit(0.U(AXI_AW.W))
val len    =   RegInit(0.U(AXI_LW.W))
val size   =   RegInit(0.U(AXI_SW.W))
val burst  =   RegInit(0.U(AXI_BW.W))
val qos    =   RegInit(0.U(AXI_QW.W))
val awid   =   RegInit(0.U(AXI_IW.W))
val awuser =   RegNext(0.U(AXI_UW.W))

val avalid =   RegNext(io.axi_awio.awvalid)
val aready =   RegNext(io.axi_awio.awready)


val cmd_en      =   Wire(Bool())
val cmd_end0    =   RegNext(cmd_en)
val cmd_hold    =   RegInit(false.B)
val cmd_hold_wid =  RegInit(false.B)
val axi_wtcmd_cnt =   RegInit(0.U(32.W))
/*********************************************************************************************************************************************************/

val u_axi_write_burst_clip = Module(new osmc_axi_write_burst_clip(CLIP_PAPAM))
io.axi_wtcmd_counter := axi_wtcmd_cnt
axi_wtcmd_cnt := Mux(avalid && aready, axi_wtcmd_cnt + 1.U, axi_wtcmd_cnt)
//AW FIFO
val u_axi_aw_fifol1 = Module(new fwft_sync_fifo(FIFO_AWL1_PARAM))
    u_axi_write_burst_clip.io.fifol1_awrio    <> u_axi_aw_fifol1.io.fifo_rio
    u_axi_aw_fifol1.io.fifo_wio.wen     :=  ((avalid & aready)  | cmd_hold) & ~u_axi_aw_fifol1.io.fifo_wio.full & ~io.wconsis
    u_axi_aw_fifol1.io.fifo_wio.wdata   :=  Cat(addr0, burst, len, size, qos)

val u_axi_aw_fifol2 = Module(new fwft_sync_fifo(FIFO_AWL2_PARAM))
    u_axi_write_burst_clip.io.fifol2_awwio    <> u_axi_aw_fifol2.io.fifo_wio

//W FIFO
val u_axi_w_fifol1  = Module(new fwft_sync_fifo(FIFO_WL1_PARAM))
    u_axi_write_burst_clip.io.fifol1_wrio     <>  u_axi_w_fifol1.io.fifo_rio
    u_axi_w_fifol1.io.fifo_wio.wen      :=  io.axi_wio.wvalid & io.axi_wio.wready
    u_axi_w_fifol1.io.fifo_wio.wdata    :=  Cat(io.axi_wio.wdata, io.axi_wio.wstrb, io.axi_wio.wlast)  

val u_axi_w_fifol2 = Module(new fwft_sync_fifo(FIFO_WL2_PARAM))
    u_axi_write_burst_clip.io.fifol2_wwio <> u_axi_w_fifol2.io.fifo_wio


//UI
val u_ui_write_cmd  = Module(new osmc_ui_write_cmd(UICMD_PARAM))
    u_ui_write_cmd.io.ui_awio       <>  io.ui_awio
    u_ui_write_cmd.io.fifol2_awrio  <>  u_axi_aw_fifol2.io.fifo_rio
    u_ui_write_cmd.io.ready_stall   <>  io.ready_stall
    u_ui_write_cmd.io.ui_wtcmd_counter   <>  io.ui_wtcmd_counter
val u_ui_write_data = Module(new osmc_ui_write_data(UIDATA_PARAM))
    u_ui_write_data.io.ui_wio       <>  io.ui_wio
    u_ui_write_data.io.fifol2_wrio  <>  u_axi_w_fifol2.io.fifo_rio

//B channel
//w channel handshak
val u_axi_wb_fifo = Module(new fwft_sync_fifo(FIFO_BO_PARAM))
    u_axi_wb_fifo.io.fifo_wio.wen    :=  io.axi_wio.wvalid & io.axi_wio.wready & io.axi_wio.wlast
    u_axi_wb_fifo.io.fifo_wio.wdata  :=  true.B
    u_axi_wb_fifo.io.fifo_rio.ren    :=  io.axi_bio.bvalid & io.axi_bio.bready
//aw channel handshak
val u_axi_awb_fifo_out = Module(new fwft_sync_fifo(FIFO_B_PARAM))
    u_axi_awb_fifo_out.io.fifo_wio.wen    :=  ((avalid & aready)  | cmd_hold_wid) & ~u_axi_awb_fifo_out.io.fifo_wio.full  & ~io.wconsis
    u_axi_awb_fifo_out.io.fifo_wio.wdata  :=  Cat(awid,awuser)
    u_axi_awb_fifo_out.io.fifo_rio.ren    :=  io.axi_bio.bvalid & io.axi_bio.bready

val w_axi_consis_table = Module(new osmc_axi_consis_table(TABLE_PARAM)) 
    w_axi_consis_table.io.ui_aio        <>  u_ui_write_cmd.io.ui_awio.bits
    w_axi_consis_table.io.ui_hsio.ready :=  u_ui_write_cmd.io.ui_awio.ready
    w_axi_consis_table.io.ui_hsio.valid <>  u_ui_write_cmd.io.ui_awio.valid
    w_axi_consis_table.io.consis_addr_io<>  io.consis_addr_io
    w_axi_consis_table.io.axi_aio.aaddr <>  io.axi_awio.awaddr
    w_axi_consis_table.io.axi_aio.aburst<>  io.axi_awio.awburst
    w_axi_consis_table.io.axi_aio.alen  <>  io.axi_awio.awlen
    w_axi_consis_table.io.axi_aio.aqos  <>  io.axi_awio.awqos
    w_axi_consis_table.io.axi_aio.aready:=  io.axi_awio.awready
    w_axi_consis_table.io.axi_aio.asize <>  io.axi_awio.awsize
    w_axi_consis_table.io.axi_aio.auser <>  io.axi_awio.awuser
    w_axi_consis_table.io.axi_aio.avalid<>  io.axi_awio.awvalid


/******************************************************************************************************************************/
    cmd_hold    :=  Mux((cmd_end0 & u_axi_aw_fifol1.io.fifo_wio.full) | (io.wconsis & cmd_end0), true.B, Mux(u_axi_aw_fifol1.io.fifo_wio.wen, false.B, cmd_hold))
    cmd_hold_wid := Mux((cmd_end0 & u_axi_awb_fifo_out.io.fifo_wio.full) | (io.wconsis & cmd_end0), true.B, Mux(u_axi_awb_fifo_out.io.fifo_wio.wen, false.B, cmd_hold_wid))
    cmd_en  :=  io.axi_awio.awvalid &   io.axi_awio.awready

    addr0  :=  Mux(cmd_en, io.axi_awio.awaddr, Mux(u_axi_aw_fifol1.io.fifo_wio.wen, 0.U, addr0))   //clear to avoid unnecessary stall                                                 
    len    :=  Mux(cmd_en, io.axi_awio.awlen , Mux(u_axi_aw_fifol1.io.fifo_wio.wen, 0.U, len  ))   //clear to avoid unnecessary stall                     
    size   :=  Mux(cmd_en, io.axi_awio.awsize, Mux(u_axi_aw_fifol1.io.fifo_wio.wen, 0.U, size ))   //clear to avoid unnecessary stall
    burst  :=  Mux(cmd_en, io.axi_awio.awburst,Mux(u_axi_aw_fifol1.io.fifo_wio.wen, 0.U, burst))   
    qos    :=  Mux(cmd_en, io.axi_awio.awqos  ,Mux(u_axi_aw_fifol1.io.fifo_wio.wen, 0.U, qos  ))  
    awid   :=  Mux(cmd_en, io.axi_awio.awid   ,Mux(u_axi_awb_fifo_out.io.fifo_wio.wen, 0.U, awid ))
    awuser :=  Mux(cmd_en, io.axi_awio.awuser,Mux(u_axi_awb_fifo_out.io.fifo_wio.wen, 0.U, awuser))
/********************************************************************************************************************************/
//ready
    io.axi_awio.awready :=  ~u_axi_aw_fifol1.io.fifo_wio.full   &   ~io.wconsis & ~cmd_hold & ~cmd_hold_wid & (~u_axi_awb_fifo_out.io.fifo_wio.full) & io.apb_config_done
    io.axi_wio.wready :=  ~u_axi_w_fifol1.io.fifo_wio.full      &  (~u_axi_wb_fifo.io.fifo_wio.full) & io.apb_config_done

//for test initialized
    io.axi_bio.bid      :=  u_axi_awb_fifo_out.io.fifo_rio.rdata(AXI_UW+AXI_IW-1, AXI_UW)
    io.axi_bio.bresp    :=  0.U   
    io.axi_bio.buser    :=  u_axi_awb_fifo_out.io.fifo_rio.rdata(AXI_UW-1, 0)   
    io.axi_bio.bvalid   :=  (~u_axi_wb_fifo.io.fifo_rio.empty) & (~u_axi_awb_fifo_out.io.fifo_rio.empty)

  
}


