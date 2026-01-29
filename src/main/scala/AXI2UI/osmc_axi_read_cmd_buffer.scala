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


//Read Reorder Buffer
class osmc_axi_read_cmd_buffer[T <: AXI2UI_PARAMETER](
    CMDBUFFER_PARAMETER   :   T
)extends Module{

//local parameter define
val CMD_PARAM   =   CMDBUFFER_PARAMETER.AXIRFIFO_PARAMETER.FIFOCMD_PARAMETER
val CT_PARAM    =   CMDBUFFER_PARAMETER.AXIRFIFO_PARAMETER.FIFOCMD_TOKEN_PARAMETER

//IO define

class CMDB_IO extends Bundle{
    //cmd buffer token_fifo
    val token_fifo_rio  = new FIFO_RIO(CT_PARAM.FIFO_WIDTH)
    val token_fifo_wio  = new FIFO_WIO(CT_PARAM.FIFO_WIDTH)  
    val cmd_fifo_rio    = new FIFO_RIO(CMD_PARAM.FIFO_WIDTH)
    val cmd_fifo_wio    = new FIFO_WIO(CMD_PARAM.FIFO_WIDTH) 
}
val io = IO(new CMDB_IO()) 

//parameter
val CMD_ADDRW   = log2Floor(CMD_PARAM.FIFO_DEPTH);
val CT_ADDRW    = log2Floor(CT_PARAM.FIFO_DEPTH); 
val cmd_rdata   = RegInit(0.U((AXI_PARAM.AXI_LENW  + AXI_PARAM.AXI_SIZEW).W))

/***************************************************************************TOKEN FWFT FIFO**************************************************************************************************/
val token_fifo  =   Module(new fwft_sync_fifo(CT_PARAM))
    token_fifo.io.fifo_wio.wen   :=  io.token_fifo_wio.wen && !io.token_fifo_wio.full 
    token_fifo.io.fifo_wio.wdata :=  io.token_fifo_wio.wdata
    token_fifo.io.fifo_rio.ren   :=  io.token_fifo_rio.ren

io.token_fifo_wio.full    :=  token_fifo.io.fifo_wio.full
io.token_fifo_rio.empty   :=  token_fifo.io.fifo_rio.empty
io.token_fifo_rio.rdata   :=  token_fifo.io.fifo_rio.rdata
/***************************************************************************CMD FWFT FIFO**************************************************************************************************/
val cmd_fifo    =   Module(new fwft_sync_fifo(CMD_PARAM))
    cmd_fifo.io.fifo_wio.wen   :=  io.cmd_fifo_wio.wen && !io.cmd_fifo_wio.full 
    cmd_fifo.io.fifo_wio.wdata :=  io.cmd_fifo_wio.wdata
    cmd_fifo.io.fifo_rio.ren   :=  io.cmd_fifo_rio.ren

io.cmd_fifo_wio.full    :=  cmd_fifo.io.fifo_wio.full
io.cmd_fifo_rio.empty   :=  cmd_fifo.io.fifo_rio.empty
cmd_rdata               :=  cmd_fifo.io.fifo_rio.rdata
io.cmd_fifo_rio.rdata   :=  cmd_rdata 
}