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

class osmc_axi_consis[T <: AXI2UI_PARAMETER](
//AXI parameter define
    CONSIS_PARAM : T
)extends Module{

    

//IO define
class CONS_IO extends Bundle{
    val consis_io   =   new CONSIS_IO()

    val consis_waddr_io =   Flipped(new CONS_ADDR_IO())
    val consis_raddr_io =   Flipped(new CONS_ADDR_IO())
}

val io = IO(new CONS_IO()) 

val TABLE_DEPTH = 1 << log2Ceil(CONSIS_PARAM.AXIWFIFO_PARAMETER.FIFOAWL1_PARAMETER.FIFO_DEPTH + CONSIS_PARAM.AXIWFIFO_PARAMETER.FIFOAWL2_PARAMETER.FIFO_DEPTH + 2)
val ADDR_WIDTH  = log2Ceil(TABLE_DEPTH);    //round up to a integer

val waddr_st    =  Wire(Vec(TABLE_DEPTH+1, UInt(CONSIS_PARAM.AXI_PARAMETER.AXI_ADDRW.W)))
val waddr_ed    =  Wire(Vec(TABLE_DEPTH+1, UInt(CONSIS_PARAM.AXI_PARAMETER.AXI_ADDRW.W)))
val raddr_st    =  Wire(Vec(TABLE_DEPTH+1, UInt(CONSIS_PARAM.AXI_PARAMETER.AXI_ADDRW.W)))
val raddr_ed    =  Wire(Vec(TABLE_DEPTH+1, UInt(CONSIS_PARAM.AXI_PARAMETER.AXI_ADDRW.W)))
val waxi_ptr    =  Wire(UInt(ADDR_WIDTH.W))
val wui_ptr     =  Wire(UInt(ADDR_WIDTH.W))
val wvalid_ptr  =  Wire(Vec(TABLE_DEPTH,Bool()))
val raxi_ptr    =  Wire(UInt(ADDR_WIDTH.W))
val rui_ptr     =  Wire(UInt(ADDR_WIDTH.W))
val rvalid_ptr  =  Wire(Vec(TABLE_DEPTH,Bool()))

val waddr_st0d  =  RegInit(0.U(CONSIS_PARAM.AXI_PARAMETER.AXI_ADDRW.W))
val waddr_ed0d  =  RegInit(0.U(CONSIS_PARAM.AXI_PARAMETER.AXI_ADDRW.W))
val raddr_st0d  =  RegInit(0.U(CONSIS_PARAM.AXI_PARAMETER.AXI_ADDRW.W))
val raddr_ed0d  =  RegInit(0.U(CONSIS_PARAM.AXI_PARAMETER.AXI_ADDRW.W))

    waddr_st0d  :=  waddr_st(0)
    waddr_ed0d  :=  waddr_ed(0)
    raddr_st0d  :=  raddr_st(0)
    raddr_ed0d  :=  raddr_ed(0)

val wconsis_cond =  Wire(Vec((TABLE_DEPTH+1),Bool()))
val rconsis_cond =  Wire(Vec((TABLE_DEPTH+1),Bool()))

val wconsis_cond0d  =  RegNext(io.consis_io.wconsis)
val rconsis_cond0d  =  RegNext(io.consis_io.rconsis)

    waxi_ptr    :=  io.consis_waddr_io.axi_ptr
    wui_ptr     :=  io.consis_waddr_io.ui_ptr
    raxi_ptr    :=  io.consis_raddr_io.axi_ptr
    rui_ptr     :=  io.consis_raddr_io.ui_ptr

for(i <-0 to TABLE_DEPTH){
    waddr_st(i) :=  io.consis_waddr_io.addr_start(i)      
    waddr_ed(i) :=  io.consis_waddr_io.addr_end  (i)  
    raddr_st(i) :=  io.consis_raddr_io.addr_start(i)
    raddr_ed(i) :=  io.consis_raddr_io.addr_end  (i)
}

//根据AXI与UI指针判断table中有效项数
for(i <-0 until TABLE_DEPTH){
    when(waxi_ptr < wui_ptr){
        when((i.U >= wui_ptr) | (i.U < waxi_ptr)){
            wvalid_ptr(i)    :=  true.B
        }   
        .otherwise{
            wvalid_ptr(i)    :=  false.B
        }
    }
    .otherwise{
        when((i.U >= wui_ptr) & (i.U < waxi_ptr)){
            wvalid_ptr(i)    :=  true.B
        }
        .otherwise{
            wvalid_ptr(i)    :=  false.B
        }
    }
}
for(i <-0 until TABLE_DEPTH){
    when(raxi_ptr < rui_ptr){
        when((i.U >= rui_ptr) | (i.U < raxi_ptr)){
            rvalid_ptr(i)    :=  true.B
        }   
        .otherwise{
            rvalid_ptr(i)    :=  false.B
        }
    }
    .otherwise{
        when((i.U >= rui_ptr) & (i.U < raxi_ptr)){
            rvalid_ptr(i)    :=  true.B
        }
        .otherwise{
            rvalid_ptr(i)    :=  false.B
        }
    }
}

//当AXI命令输入时，分别与表内各有效项比较是否有重叠地址
for(i <- 0 until TABLE_DEPTH){
    wconsis_cond(i) :=  (((waddr_st(0)  >= raddr_st(i+1)) && (waddr_st(0)  <= raddr_ed(i+1) ))
                        |((waddr_ed(0)  >= raddr_st(i+1)) && (waddr_ed(0)  <= raddr_ed(i+1) ))) && rvalid_ptr(i)
    rconsis_cond(i) :=  (((raddr_st(0)  >= waddr_st(i+1)) && (raddr_st(0)  <= waddr_ed(i+1) ))
                        |((raddr_ed(0)  >= waddr_st(i+1)) && (raddr_ed(0)  <= waddr_ed(i+1) ))) && wvalid_ptr(i)
}
val one_cyc_wconf   =  Wire(Bool())
val one_cyc_rconf   =  Wire(Bool())
    one_cyc_wconf   :=  (((waddr_st(0)  >= raddr_st0d) && (waddr_st(0)  <= raddr_ed0d ))    |   ((waddr_ed(0)  >= raddr_st0d) && (waddr_ed(0)  <= raddr_ed0d )))    && ~rconsis_cond0d
    one_cyc_rconf   :=  (((raddr_st(0)  >= waddr_st0d) && (raddr_st(0)  <= waddr_ed0d ))    |   ((raddr_ed(0)  >= /* raddr_ed0d */waddr_st0d) && (raddr_ed(0)  <= waddr_ed0d )))    && ~wconsis_cond0d

    wconsis_cond(TABLE_DEPTH) :=    one_cyc_wconf   &&  Cat(rvalid_ptr).orR           
    rconsis_cond(TABLE_DEPTH) :=    one_cyc_rconf   &&  Cat(wvalid_ptr).orR   

    io.consis_io.wconsis    :=  Cat(wconsis_cond).orR     
    io.consis_io.rconsis    :=  Cat(rconsis_cond).orR





















}


