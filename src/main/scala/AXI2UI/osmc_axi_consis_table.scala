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
//用于AXI2UI模块一致性检查的地址记录表

class osmc_axi_consis_table[T <: AXI2UI_PARAMETER](
//AXI parameter define
    TABLE_PARAM : T
)extends Module{

//IO define
class UI_HSIO extends Bundle{
    val ready   = Input(Bool())
    val valid   = Input(Bool())
} 

class TABLE_IO extends Bundle{
    val axi_aio   = new AXI_CMDIO()
    val ui_aio    = Flipped(new CMDIO())
    val ui_hsio   = new UI_HSIO()
    val consis_addr_io  = new CONS_ADDR_IO()
}
val io = IO(new TABLE_IO)

//local parameter
val TABLE_DEPTH = 1 << log2Ceil(TABLE_PARAM.AXIWFIFO_PARAMETER.FIFOAWL1_PARAMETER.FIFO_DEPTH + TABLE_PARAM.AXIWFIFO_PARAMETER.FIFOAWL2_PARAMETER.FIFO_DEPTH + 2)
val ADDR_WIDTH  = log2Ceil(TABLE_DEPTH);    //round up to a integer

val AXI_AW      =   AXI_PARAM.AXI_ADDRW


/******************************************************************************************************************************/

/**************************************************************** FIFO *******************************************************************************/

//地址记录表是类似于一个FIFO的结构，由指针地址表示目前table中有效的项
val axi_ptr  = RegInit(0.U(ADDR_WIDTH.W))
val ui_ptr   = RegInit(0.U(ADDR_WIDTH.W))
val axi_en   = Wire(Bool())
val ui_en    = Wire(Bool())
val vaild_data    = RegInit(0.U((ADDR_WIDTH+1).W))    //handshake
//val full    = Wire(Bool())

//对axi输入的cmd进行首末地址计算
val addr0_start =   Wire(UInt(AXI_AW.W))
val addr0_end   =   Wire(UInt(AXI_AW.W))
val addr0_ext   =   Wire(Bool())

    addr0_start :=  io.axi_aio.aaddr
    addr0_end   :=  io.axi_aio.aaddr + ((io.axi_aio.alen+1.U)<<io.axi_aio.asize)
    addr0_ext   :=  Mux(((io.axi_aio.alen+1.U)<<io.axi_aio.asize) > (TABLE_PARAM.UI_PARAMETER.UI_DATAW/8).U, true.B, false.B) //if cmd is extended

    axi_ptr    :=  Mux(axi_en,axi_ptr + 1.U, axi_ptr)
    ui_ptr     :=  Mux(ui_en ,ui_ptr  + 1.U, ui_ptr )

val addr_se_memory  = RegInit(VecInit.fill(TABLE_DEPTH)(0.U((AXI_AW*2 + 1).W)))//Reg(Vec(TABLE_DEPTH, UInt((AXI_AW*2+1).W)))//
addr_se_memory(axi_ptr)  :=  Mux(axi_en, Cat(addr0_ext, addr0_end, addr0_start), addr_se_memory(axi_ptr))

//当AXI en时table写入项，当UI en且输出地址为当前AXI命令的末地址时清除项。addr0_ext信号根据当前AXI末地址-首地址是否大于UI的地址自增量来判断本次AXI命令是否被拓展为了多个UI命令
    axi_en :=  io.axi_aio.avalid  && io.axi_aio.aready
    ui_en  :=  io.ui_hsio.valid   && io.ui_hsio.ready  && (((~addr_se_memory(ui_ptr)(AXI_AW*2)) && (io.ui_aio.addr === addr_se_memory(ui_ptr)(AXI_AW -1, 0)))    |   ((addr_se_memory(ui_ptr)(AXI_AW*2)) && (io.ui_aio.addr === addr_se_memory(ui_ptr)(AXI_AW*2 -1, AXI_AW))))  
//将table的指针与表内项输出到consis模块作一致性比较
    io.consis_addr_io.addr_end(0)       :=  addr0_end
    io.consis_addr_io.addr_start(0)     :=  addr0_start

for(i <- 1 to TABLE_DEPTH){
    io.consis_addr_io.addr_end(i)       :=  addr_se_memory(i-1)(AXI_AW*2 -1, AXI_AW)                                           
    io.consis_addr_io.addr_start(i)     :=  addr_se_memory(i-1)(AXI_AW-1   , 0)                                        
}                                       

    io.consis_addr_io.axi_ptr   :=  axi_ptr
    io.consis_addr_io.ui_ptr    :=  ui_ptr

}

















