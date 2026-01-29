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




class osmc_ui_read_cmd[T <: AXI2UI_PARAMETER](
//AXI parameter define
    UICMD_PARAMETER   :   T
)extends Module{


/*********************************************************************************************************************************************************/
//local parameter define
val FIFO_ARL2_PARAM = UICMD_PARAMETER.AXIRFIFO_PARAMETER.FIFOARL2_PARAMETER
val UI_PAPAM        = UICMD_PARAMETER.UI_PARAMETER
val TOKEN_PARAM     = UICMD_PARAMETER.TOKEN_PARAMETER


class UI_READ_CMDIO extends Bundle{
    //UI write address
    val ui_ario = Decoupled(new CMDIO())
    //UI write data
    //FIFO
    val fifol2_arrio    = Flipped(new FIFO_RIO(FIFO_ARL2_PARAM.FIFO_WIDTH))    //connect fifo L1
    val ready_stall =   Input(Bool())
    val ui_rdcmd_counter = Output(UInt(32.W))
}
//IO define
val io = IO(new UI_READ_CMDIO())  
/*********************************************************************************************************************************************************/
val ui_rdcmd_cnt = RegInit(0.U(32.W))


    io.fifol2_arrio.ren := io.ui_ario.fire
    ui_rdcmd_cnt :=  Mux(io.fifol2_arrio.ren & io.ui_ario.ready, ui_rdcmd_cnt + 1.U, ui_rdcmd_cnt) ////////////
io.ui_rdcmd_counter := ui_rdcmd_cnt
io.ui_ario.valid    := !io.fifol2_arrio.empty   &   ~io.ready_stall 
io.ui_ario.bits.addr    := io.fifol2_arrio.rdata(UI_PAPAM.UI_ADDRW-1, 0)
io.ui_ario.bits.token   := io.fifol2_arrio.rdata(FIFO_ARL2_PARAM.FIFO_WIDTH-1, UI_PAPAM.UI_ADDRW)
io.ui_ario.bits.pri     := 0.U
}


