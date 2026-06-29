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


class osmc_axi_read[T <: AXI2UI_PARAMETER](
//AXI parameter define
    AXIR_PARAMETER   :   T
)extends Module{


class AXI_READIO extends Bundle{
    //AXI read address
    val axi_ario= new AXI_ARIO()
    //AXI read data
    val axi_rio = new AXI_RIO()        
    //UI read
    val ui_ario = Decoupled(new CMDIO())
    val ui_rio  = Flipped(Decoupled(new RdDataIO()))
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

/*********************************************************************************************************************************************************/

//IO define
val io = IO(new AXI_READIO())  
/*************************************************************** consistency ***********************************************************************/

val axi_rdcmd_cnt       = RegInit(0.U(32.W))
val ui_rdback_cnt       = RegInit(0.U(32.W))
val ui_rdcmd_counter    = RegInit(0.U(32.W))
/*********************************************************************************************************************************************************/


val u_axi_read_burst_clip                            = Module(new osmc_axi_read_burst_clip(CLIP_PAPAM))
val UnSplitCommandQueue                              = Module(new Queue(new UnSplitCommandQueueBundle, 4))
val SplitCommandQueue                                = Module(new Queue(new SplitCommandQueueBundle(AXI_PARAM.AXI_IDW), 8))    
val VirtualChannel                                   = Module(new osmc_axi_virtual_channel).io
val u_ReadDataQueue                                  = Module(new ConvertQueue(new ReadQueueBundle(1),depth = 8))
val r_axi_consis_table                               = Module(new osmc_axi_consis_table(TABLE_PARAM))
//AR FIFO
    io.ui_rdback_counter                            := ui_rdback_cnt
    io.axi_rdcmd_counter                            := axi_rdcmd_cnt
    axi_rdcmd_cnt                                   := Mux(io.axi_ario.arready & io.axi_ario.arvalid, axi_rdcmd_cnt + 1.U, axi_rdcmd_cnt)
    ui_rdback_cnt                                   := Mux( io.axi_rio.rlast &io.axi_rio.rvalid &io.axi_rio.rready, ui_rdback_cnt + 1.U, ui_rdback_cnt)
/*********************************************************************************************************************************************************/
    u_axi_read_burst_clip.io.UnSplitCommandQueue    :<>= UnSplitCommandQueue.io.deq
    UnSplitCommandQueue.io.enq.valid                := io.axi_ario.arvalid & (~io.rconsis)
    UnSplitCommandQueue.io.enq.bits.addr            := io.axi_ario.araddr 
    UnSplitCommandQueue.io.enq.bits.len             := io.axi_ario.arlen
    UnSplitCommandQueue.io.enq.bits.burst           := io.axi_ario.arburst
    UnSplitCommandQueue.io.enq.bits.id              := io.axi_ario.arid 
    UnSplitCommandQueue.io.enq.bits.size            := io.axi_ario.arsize
    UnSplitCommandQueue.io.enq.bits.qos             := io.axi_ario.arqos
/*********************************************************************************************************************************************************/
    VirtualChannel.SplitCommandQueue                <> SplitCommandQueue.io.deq
    VirtualChannel.UiCommand                        <> io.ui_ario
    VirtualChannel.ui_rio                           <> io.ui_rio
/*********************************************************************************************************************************************************/
   u_axi_read_burst_clip.io.SplitCommandQueue       <> SplitCommandQueue.io.enq
/********************************************************************************************************************************/
//R FIFO
    VirtualChannel.RobDeq                           <> u_ReadDataQueue.io.enq
    u_ReadDataQueue.io.deq.ready                    := io.axi_rio.rready
    u_ReadDataQueue.io.deq.valid                    <> io.axi_rio.rvalid
    io.axi_rio.rdata                                := u_ReadDataQueue.io.deq.bits.data(0)
    io.axi_rio.rlast                                := u_ReadDataQueue.io.deq.bits.last(0)
    io.axi_rio.rid                                  := u_ReadDataQueue.io.deq.bits.id(0)
    io.axi_rio.ruser                                := 0.U
    r_axi_consis_table.io.ui_aio                    <> VirtualChannel.UiCommand.bits    
    r_axi_consis_table.io.ui_hsio.ready             := VirtualChannel.UiCommand.ready      
    r_axi_consis_table.io.ui_hsio.valid             <> VirtualChannel.UiCommand.valid                                
    r_axi_consis_table.io.consis_addr_io            <> io.consis_addr_io
    r_axi_consis_table.io.axi_aio.aaddr             <> io.axi_ario.araddr
    r_axi_consis_table.io.axi_aio.aburst            <> io.axi_ario.arburst
    r_axi_consis_table.io.axi_aio.alen              <> io.axi_ario.arlen
    r_axi_consis_table.io.axi_aio.aqos              <> io.axi_ario.arqos
    r_axi_consis_table.io.axi_aio.aready            := io.axi_ario.arready
    r_axi_consis_table.io.axi_aio.asize             <> io.axi_ario.arsize
    r_axi_consis_table.io.axi_aio.auser             <> io.axi_ario.aruser
    r_axi_consis_table.io.axi_aio.avalid            <> io.axi_ario.arvalid
    io.axi_ario.arready                             := UnSplitCommandQueue.io.enq.ready  &   ~io.rconsis &   io.apb_config_done                  
    io.axi_rio.rresp                                := 0.U
    ui_rdcmd_counter                                := ui_rdcmd_counter + io.ui_ario.fire
    io.ui_rdcmd_counter                             := ui_rdcmd_counter
}

