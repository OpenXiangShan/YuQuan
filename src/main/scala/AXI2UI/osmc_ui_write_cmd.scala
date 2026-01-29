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




class osmc_ui_write_cmd[T <: AXI2UI_PARAMETER](
//AXI parameter define
    UICMD_PARAMETER   :   T
)extends Module{


/*********************************************************************************************************************************************************/
//local parameter define
val FIFO_AWL2_PARAM = UICMD_PARAMETER.AXIWFIFO_PARAMETER.FIFOAWL2_PARAMETER
val UI_PAPAM        = UICMD_PARAMETER.UI_PARAMETER
val TOKEN_PARAM     = UICMD_PARAMETER.TOKEN_PARAMETER

class UI_WRITE_CMDIO extends Bundle{
    //UI write address
    val ui_awio = Decoupled(new CMDIO())
    //UI write data
    //FIFO
    val fifol2_awrio    = Flipped(new FIFO_RIO(FIFO_AWL2_PARAM.FIFO_WIDTH))    //connect fifo L1
    val ready_stall =   Input(Bool())
    val ui_wtcmd_counter = Output(UInt(32.W))
}
//IO define
val io = IO(new UI_WRITE_CMDIO())  
/*********************************************************************************************************************************************************/


val ui_wtcmd_cnt = RegInit(0.U(32.W))
io.ui_wtcmd_counter := ui_wtcmd_cnt

    ui_wtcmd_cnt := Mux(io.ui_awio.fire, ui_wtcmd_cnt + 1.U, ui_wtcmd_cnt) 

    io.fifol2_awrio.ren := io.ui_awio.fire
io.ui_awio.valid   := !io.fifol2_awrio.empty   &   ~io.ready_stall   
io.ui_awio.bits.addr    := io.fifol2_awrio.rdata(UI_PAPAM.UI_ADDRW-1, 0)
io.ui_awio.bits.token   := io.fifol2_awrio.rdata(FIFO_AWL2_PARAM.FIFO_WIDTH-1, UI_PAPAM.UI_ADDRW)
io.ui_awio.bits.pri     := 0.U

}


