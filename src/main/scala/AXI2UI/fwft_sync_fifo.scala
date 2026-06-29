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

//FWFT类型的同步FIFO模块

package OpenMc

import chisel3._
import chisel3.util._
import chisel3.experimental.FlatIO
import java.util.ResourceBundle

class fwft_sync_fifo[T <: FIFO_PARAMETER](
//FIFO parameter define 
    FIFO_PARAMETER    :  T 
)extends Module{

//IO define
class FIFO_IO extends Bundle{
    val fifo_wio    = new FIFO_WIO(FIFO_PARAMETER.FIFO_WIDTH)
    val fifo_rio    = new FIFO_RIO(FIFO_PARAMETER.FIFO_WIDTH)
}
val io = IO(new FIFO_IO)

//local parameter
val ADDR_WIDTH = log2Floor(FIFO_PARAMETER.FIFO_DEPTH); 

/******************************************************************************************************************************/
val w_addr  = RegInit(0.U(ADDR_WIDTH.W))
val r_addr  = RegInit(0.U(ADDR_WIDTH.W))
val w_en    = Wire(Bool())
val r_en    = Wire(Bool())
val data_bypass =   Wire(Bool())

w_en    :=  io.fifo_wio.wen && (!io.fifo_wio.full   |   io.fifo_rio.ren)
r_en    :=  io.fifo_rio.ren && (!io.fifo_rio.empty  |   io.fifo_wio.wen)
w_addr  :=  Mux(w_en, w_addr + 1.U, w_addr)
r_addr  :=  Mux(r_en, r_addr + 1.U, r_addr)

val memory = Reg(Vec(FIFO_PARAMETER.FIFO_DEPTH, UInt(FIFO_PARAMETER.FIFO_WIDTH.W)))
val r_data  = Wire(UInt(FIFO_PARAMETER.FIFO_WIDTH.W))

memory(w_addr)  :=  Mux(w_en, io.fifo_wio.wdata, memory(w_addr))

//r_data为wire类型，故而r_data可以在r_en使能前便被准备
//data_bypass可在fifo为空时直接将输入端的数据接入到r_data上
data_bypass :=  (io.fifo_rio.empty)
r_data  :=  Mux(data_bypass, io.fifo_wio.wdata, memory(r_addr))

//margin calculate
val vaild_data    = RegInit(0.U((log2Floor(FIFO_PARAMETER.FIFO_DEPTH)+1).W))
switch(Cat(w_en,r_en)){
    is("b01".U){
        vaild_data    :=  vaild_data - 1.U
    }
    is("b10".U){
        vaild_data    :=  vaild_data + 1.U
    }
    is(("b00".U), ("b11".U)){
        vaild_data    :=  vaild_data
    }
}

io.fifo_wio.full    :=  Mux((vaild_data === FIFO_PARAMETER.FIFO_DEPTH.U), 1.U, 0.U)
io.fifo_rio.empty   :=  Mux((vaild_data === 0.U), 1.U, 0.U)
io.fifo_rio.rdata   :=  r_data
}
