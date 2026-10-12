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



class osmc_ui_write_data[T <: AXI2UI_PARAMETER](
//AXI parameter define
    UIDATA_PARAMETER   :   T
)extends Module{

/*********************************************************************************************************************************************************/
//local parameter define
val FIFO_WL2_PARAM  = UIDATA_PARAMETER.AXIWFIFO_PARAMETER.FIFOWL2_PARAMETER
val UI_PAPAM        = UIDATA_PARAMETER.UI_PARAMETER
val TOKEN_PARAM     = UIDATA_PARAMETER.TOKEN_PARAMETER

class UI_WRITE_DATAIO extends Bundle{
    //UI write address
    val ui_wio  = Decoupled(new WrDataIO()) 
    //UI write data
    //FIFO
    val fifol2_wrio    = Flipped(new FIFO_RIO(FIFO_WL2_PARAM.FIFO_WIDTH))    //connect fifo L1
}

//IO define
val io = IO(new UI_WRITE_DATAIO())  
/*********************************************************************************************************************************************************/
io.fifol2_wrio.ren := io.ui_wio.ready & !io.fifol2_wrio.empty
val wvalid  = Wire(Bool())    
val wdata   = Wire(UInt(UI_PAPAM.UI_DATAW.W))
val wstrb   = Wire(UInt(UI_PAPAM.UI_STRBW.W))

    wdata   :=  io.fifol2_wrio.rdata(UI_PAPAM.UI_DATAW-1, 0)
    wstrb   :=  io.fifol2_wrio.rdata(UI_PAPAM.UI_DATAW+UI_PAPAM.UI_STRBW-1, UI_PAPAM.UI_DATAW)
    wvalid  :=  io.fifol2_wrio.ren

io.ui_wio.valid := !io.fifol2_wrio.empty
io.ui_wio.bits.wdata  := wdata 
io.ui_wio.bits.wstrb  := wstrb 
}


