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
class osmc_axi_read_rob[T <: AXI2UI_PARAMETER](
    RROB_PARAMETER   :   T
)extends Module{

//local parameter define
val UI_DW       =   RROB_PARAMETER.UI_PARAMETER.UI_DATAW
val CT_PARAM    =   RROB_PARAMETER.AXIRFIFO_PARAMETER.FIFOCMD_TOKEN_PARAMETER
val TOKEN_WIDTH =   RROB_PARAMETER.TOKEN_PARAMETER.TOKEN_WIDTH
val io = IO(new Bundle {
    //UI write data
    val ui_rio  = Flipped(Decoupled(new RdDataIO()))    
    //cmd buffer token_fifo
    val cmd_fifo_rio    = Flipped(new FIFO_RIO(CT_PARAM.FIFO_WIDTH))
    //burst_clip
    val rdata    = Decoupled(UInt(UI_DW.W))
}) 
//define storage
val UiDataMem   = SyncReadMem(1<<TOKEN_WIDTH,UInt(UI_DW.W))//128 entrys 
val ValidBitMap = RegInit(VecInit.fill(1<<TOKEN_WIDTH)(false.B))

    
    UiDataMem.do_readWrite(io.ui_rio.bits.rtoken, io.ui_rio.bits.rdata, io.ui_rio.valid , true.B)
    io.rdata.bits := UiDataMem.do_read(io.cmd_fifo_rio.rdata,ValidBitMap(io.cmd_fifo_rio.rdata) )
    io.rdata.valid := RegNext(Mux(io.cmd_fifo_rio.empty,false.B,ValidBitMap(io.cmd_fifo_rio.rdata) ))
    io.cmd_fifo_rio.ren := ValidBitMap(io.cmd_fifo_rio.rdata) &io.rdata.fire

    io.ui_rio.ready := true.B

switch(Cat(io.ui_rio.valid, io.cmd_fifo_rio.ren)){
    is("b00".U){
        ValidBitMap               :=  ValidBitMap}
    is("b01".U){
        ValidBitMap(io.cmd_fifo_rio.rdata) :=  false.B}
    is("b10".U){
        ValidBitMap(io.ui_rio.bits.rtoken)  :=  true.B}
    is("b11".U){
        ValidBitMap(io.cmd_fifo_rio.rdata) :=  false.B
        ValidBitMap(io.ui_rio.bits.rtoken)  :=  true.B}
} 
}