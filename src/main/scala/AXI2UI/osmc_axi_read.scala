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



class osmc_axi_read[T <: AXI2UI_PARAMETER](
//AXI parameter define
    AXIR_PARAMETER   :   T
)extends Module{

class TOKEN_COUNTIO_R extends Bundle{
    val token_aren      =   Output(Bool())
    val token_arready   =   Output(Bool())
}
class AXI_READIO extends Bundle{
    //AXI read address
    val axi_ario= new AXI_ARIO()
    //AXI read data
    val axi_rio = new AXI_RIO()        
    //UI read
    val ui_ario = Decoupled(new CMDIO())
    val ui_rio  = Flipped(Decoupled(new RdDataIO()))
    val ready_stall =   Output(Bool())
    //consis
    val rconsis =   Input(Bool())
    val consis_addr_io  = new CONS_ADDR_IO()
    val ui_rdcmd_counter = Output(UInt(32.W))
    val axi_rdcmd_counter = Output(UInt(32.W))
    val ui_rdback_counter = Output(UInt(32.W))
    //apb config done 
    val apb_config_done = Flipped(Bool())
}

/*********************************************************************************************************************************************************/
//local parameter define
val FIFO_ARL1_PARAM = AXIR_PARAMETER.AXIRFIFO_PARAMETER.FIFOARL1_PARAMETER
val FIFO_ARL2_PARAM = AXIR_PARAMETER.AXIRFIFO_PARAMETER.FIFOARL2_PARAMETER
val FIFO_RL1_PARAM  = AXIR_PARAMETER.AXIRFIFO_PARAMETER.FIFORL1_PARAMETER
val CLIP_PAPAM      = AXIR_PARAMETER
val UICMD_PARAM     = AXIR_PARAMETER
val UIDATA_PARAM    = AXIR_PARAMETER

val RROB_PARAM      = AXIR_PARAMETER
val CMDBUFFER_PARAMETER = AXIR_PARAMETER
val TABLE_PARAM     = AXIR_PARAMETER  

val AXI_BW  =   AXIR_PARAMETER.AXI_PARAMETER.AXI_BURSTW
val AXI_AW  =   AXIR_PARAMETER.AXI_PARAMETER.AXI_ADDRW
val AXI_LW  =   AXIR_PARAMETER.AXI_PARAMETER.AXI_LENW
val AXI_SW  =   AXIR_PARAMETER.AXI_PARAMETER.AXI_SIZEW
val AXI_QW  =   AXIR_PARAMETER.AXI_PARAMETER.AXI_QOSW
/*********************************************************************************************************************************************************/

//IO define
val io = IO(new AXI_READIO())  
/*************************************************************** consistency ***********************************************************************/
val addr0  =   RegInit(0.U(AXI_AW.W))
val len    =   RegInit(0.U(AXI_LW.W))
val size   =   RegInit(0.U(AXI_SW.W))
val burst  =   RegInit(0.U(AXI_BW.W))
val qos    =   RegInit(0.U(AXI_QW.W))
val avalid =   RegNext(io.axi_ario.arvalid)
val aready =   RegNext(io.axi_ario.arready)
val arid   =   RegInit(0.U(AXI_PARAM.AXI_IDW.W))
val aruser =   RegInit(0.U(AXI_PARAM.AXI_USERW.W))

val cmd_en      =   Wire(Bool())
val cmd_end0    =   RegNext(cmd_en)
val cmd_hold    =   RegInit(false.B)
val axi_rdcmd_cnt =   RegInit(0.U(32.W))
val ui_rdback_cnt =   RegInit(0.U(32.W))
/*********************************************************************************************************************************************************/
val u_axi_read_burst_clip = Module(new osmc_axi_read_burst_clip(CLIP_PAPAM))
//AR FIFO
    io.ui_rdback_counter := ui_rdback_cnt
    io.axi_rdcmd_counter := axi_rdcmd_cnt
    axi_rdcmd_cnt := Mux(avalid & aready, axi_rdcmd_cnt + 1.U, axi_rdcmd_cnt)
    ui_rdback_cnt := Mux( io.axi_rio.rlast &io.axi_rio.rvalid &io.axi_rio.rready, ui_rdback_cnt + 1.U, ui_rdback_cnt)
val u_axi_ar_fifol1 = Module(new fwft_sync_fifo(FIFO_ARL1_PARAM))
    u_axi_read_burst_clip.io.fifol1_arrio   <> u_axi_ar_fifol1.io.fifo_rio
    u_axi_ar_fifol1.io.fifo_wio.wen         :=  ((avalid & aready)  | cmd_hold) & ~u_axi_ar_fifol1.io.fifo_wio.full & ~io.rconsis
    u_axi_ar_fifol1.io.fifo_wio.wdata       :=  Cat(addr0, burst, len, size, qos)

val u_axi_ar_fifol2 = Module(new fwft_sync_fifo(FIFO_ARL2_PARAM))
   u_axi_read_burst_clip.io.fifol2_arwio    <> u_axi_ar_fifol2.io.fifo_wio
val rdata_counter   = RegInit(0.U(1.W)) 
rdata_counter := Mux((io.axi_rio.rready && io.axi_rio.rvalid),rdata_counter + 1.U ,rdata_counter)

//R FIFO
val u_axi_r_fifol1  = Module(new fwft_sync_fifo(FIFO_RL1_PARAM))
    u_axi_read_burst_clip.io.fifol1_rwio    <>  u_axi_r_fifol1.io.fifo_wio
    u_axi_r_fifol1.io.fifo_rio.ren  :=  io.axi_rio.rready & io.axi_rio.rvalid
    io.axi_rio.rdata    :=  u_axi_r_fifol1.io.fifo_rio.rdata(FIFO_RL1_PARAM.FIFO_WIDTH-2, 0)
    io.axi_rio.rlast    := rdata_counter === 1.U && io.axi_rio.rready & io.axi_rio.rvalid
    val rvalid  =   RegNext(u_axi_r_fifol1.io.fifo_rio.ren)

val u_axi_rrob = Module(new osmc_axi_read_rob(RROB_PARAM))
    u_axi_read_burst_clip.io.rrob_io        <>  u_axi_rrob.io.rdata
    u_axi_rrob.io.ui_rio                    <>  io.ui_rio

val u_axi_r_cmdbuffer = Module(new osmc_axi_read_cmd_buffer(CMDBUFFER_PARAMETER))
    u_axi_rrob.io.cmd_fifo_rio              <>  u_axi_r_cmdbuffer.io.token_fifo_rio
    u_axi_read_burst_clip.io.cmd_fifo_rio   <>  u_axi_r_cmdbuffer.io.cmd_fifo_rio
    u_axi_read_burst_clip.io.cmd_fifo_wio   <>  u_axi_r_cmdbuffer.io.cmd_fifo_wio
    u_axi_read_burst_clip.io.token_fifo_wio <>  u_axi_r_cmdbuffer.io.token_fifo_wio

//暂存ARID，当前ROB将严格保序返回数据
val u_aridQueue  = Module(new Queue(UInt((AXI_PARAM.AXI_IDW + AXI_PARAM.AXI_USERW).W), 256))
u_aridQueue.io.enq.bits  := Cat(arid, aruser) 
u_aridQueue.io.enq.valid := ((avalid & aready)  | cmd_hold) & ~u_axi_ar_fifol1.io.fifo_wio.full & ~io.rconsis 
u_aridQueue.io.deq.ready := io.axi_rio.rvalid & io.axi_rio.rready & io.axi_rio.rlast
io.axi_rio.rid           := u_aridQueue.io.deq.bits(AXI_PARAM.AXI_IDW + AXI_PARAM.AXI_USERW-1, AXI_PARAM.AXI_USERW)
io.axi_rio.ruser         := u_aridQueue.io.deq.bits(AXI_PARAM.AXI_USERW-1,0) 
io.axi_rio.rvalid        := ~u_axi_r_fifol1.io.fifo_rio.empty & u_aridQueue.io.deq.valid
//UI
val u_ui_read_cmd  = Module(new osmc_ui_read_cmd(UICMD_PARAM))
    u_ui_read_cmd.io.ui_ario       <>  io.ui_ario
    u_ui_read_cmd.io.fifol2_arrio  <>  u_axi_ar_fifol2.io.fifo_rio
    u_ui_read_cmd.io.ready_stall   :=  io.ready_stall
    io.ui_rdcmd_counter :=  u_ui_read_cmd.io.ui_rdcmd_counter

val r_axi_consis_table = Module(new osmc_axi_consis_table(TABLE_PARAM)) 
    r_axi_consis_table.io.ui_aio        <>  u_ui_read_cmd.io.ui_ario.bits
    r_axi_consis_table.io.ui_hsio.ready :=  u_ui_read_cmd.io.ui_ario.ready
    r_axi_consis_table.io.ui_hsio.valid <>  u_ui_read_cmd.io.ui_ario.valid
    r_axi_consis_table.io.consis_addr_io<>  io.consis_addr_io
    r_axi_consis_table.io.axi_aio.aaddr <>  io.axi_ario.araddr
    r_axi_consis_table.io.axi_aio.aburst<>  io.axi_ario.arburst
    r_axi_consis_table.io.axi_aio.alen  <>  io.axi_ario.arlen
    r_axi_consis_table.io.axi_aio.aqos  <>  io.axi_ario.arqos
    r_axi_consis_table.io.axi_aio.aready:=  io.axi_ario.arready
    r_axi_consis_table.io.axi_aio.asize <>  io.axi_ario.arsize
    r_axi_consis_table.io.axi_aio.auser <>  io.axi_ario.aruser
    r_axi_consis_table.io.axi_aio.avalid<>  io.axi_ario.arvalid

/******************************************************************************************************************************/
    cmd_hold    :=  Mux((cmd_end0 & u_axi_ar_fifol1.io.fifo_wio.full) | (io.rconsis & cmd_end0), true.B, Mux(u_axi_ar_fifol1.io.fifo_wio.wen, false.B, cmd_hold))
    cmd_en  :=  io.axi_ario.arvalid &   io.axi_ario.arready

    addr0  :=  Mux(cmd_en, io.axi_ario.araddr, Mux(u_axi_ar_fifol1.io.fifo_wio.wen, 0.U, addr0))   //clear to avoid unnecessary stall                                                 
    len    :=  Mux(cmd_en, io.axi_ario.arlen , Mux(u_axi_ar_fifol1.io.fifo_wio.wen, 0.U, len  ))   //clear to avoid unnecessary stall                     
    size   :=  Mux(cmd_en, io.axi_ario.arsize, Mux(u_axi_ar_fifol1.io.fifo_wio.wen, 0.U, size ))   //clear to avoid unnecessary stall
    burst  :=  Mux(cmd_en, io.axi_ario.arburst,Mux(u_axi_ar_fifol1.io.fifo_wio.wen, 0.U, burst))   
    qos    :=  Mux(cmd_en, io.axi_ario.arqos  ,Mux(u_axi_ar_fifol1.io.fifo_wio.wen, 0.U, qos  ))  
    arid   :=  Mux(cmd_en, io.axi_ario.arid   ,Mux(u_axi_ar_fifol1.io.fifo_wio.wen, 0.U, arid ))
    aruser :=  Mux(cmd_en, io.axi_ario.aruser,Mux(u_axi_ar_fifol1.io.fifo_wio.wen, 0.U, aruser))
/********************************************************************************************************************************/

//UI阻塞，当下发且未返回的read token达到127个时，阻塞UI，防止rrob溢出
//ui cmd stall
val MAX_READ        = AXIR_PARAMETER.AXIRFIFO_PARAMETER.RROB_ADDQ_PARAMETER.FIFO_DEPTH
val MAX_READW       = log2Floor(MAX_READ)

val rtoken_cnt  =   RegInit(0.U(MAX_READW.W))
val rtoken_decond   =   Wire(Bool())
val rtoken_adcond   =   Wire(Bool())
    rtoken_decond   :=  u_axi_rrob.io.cmd_fifo_rio.ren
    rtoken_adcond   :=  u_ui_read_cmd.io.ui_ario.valid & u_ui_read_cmd.io.ui_ario.ready
switch(Cat(rtoken_adcond,rtoken_decond)){
    is("b01".U){
        rtoken_cnt    :=  rtoken_cnt - 1.U
    }
    is("b10".U){
        rtoken_cnt    :=  rtoken_cnt + 1.U
    }
    is(("b00".U), ("b11".U)){
        rtoken_cnt    :=  rtoken_cnt
    }
}
    io.ready_stall  :=  rtoken_cnt(MAX_READW-1, 0).andR//
    io.axi_ario.arready :=  ~u_axi_ar_fifol1.io.fifo_wio.full  & u_aridQueue.io.enq.ready&   ~io.rconsis &  ~cmd_hold & io.apb_config_done  
    io.axi_rio.rresp    :=  0.U
}

