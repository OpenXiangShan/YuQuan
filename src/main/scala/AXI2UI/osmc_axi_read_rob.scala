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
import javax.xml.catalog.Catalog
import org.json4s.native.JsonParser.Token
class TokenEnqBundle extends SplitCommandQueueBundle(AXI_PARAM.AXI_IDW){
    val token = UInt(BUNDLE_PARAM.TOKEN_WIDTH.W)
}
//Read Reorder Buffer
class osmc_axi_read_rob (PerRobEntryNum : Int )extends Module{
//local parameter define
val UI_DW       =   AXI2UI_PARAM.UI_PARAMETER.UI_DATAW
val TOKEN_WIDTH =   log2Ceil(PerRobEntryNum)
val io = IO(new Bundle{
    val TokenQueueEnq       = Flipped(DecoupledIO(new TokenEnqBundle)) 
    val RobDeq              = DecoupledIO(Vec(2,new ReadQueueBundle(1)))
    val ui_rio              = Flipped(DecoupledIO(new RdDataIO(log2Ceil(PerRobEntryNum))))
})
val TokenQueue              = Module(new Queue(new TokenEnqBundle,PerRobEntryNum))
val UiDataMem               = SyncReadMem(1<<TOKEN_WIDTH,UInt(UI_DW.W))//128 entrys 
val ValidBitMap             = RegInit(VecInit.fill(1<<TOKEN_WIDTH)(false.B))
val ReadData                = UiDataMem.do_read((TokenQueue.io.deq.bits.token),ValidBitMap(TokenQueue.io.deq.bits.token)& io.RobDeq.ready)
    TokenQueue.io.enq                 <> io.TokenQueueEnq
    UiDataMem.do_readWrite(io.ui_rio.bits.rtoken, io.ui_rio.bits.rdata, io.ui_rio.valid , true.B)
    io.RobDeq.bits(0).data            := ReadData((UI_DW >> 1) - 1,0).asTypeOf(io.RobDeq.bits(0).data)
    io.RobDeq.bits(1).data            := ReadData(UI_DW - 1,UI_DW >> 1).asTypeOf(io.RobDeq.bits(1).data)
    io.RobDeq.bits(0).id              := TokenQueue.io.deq.bits.id.asTypeOf(io.RobDeq.bits(0).id)
    io.RobDeq.bits(1).id              := TokenQueue.io.deq.bits.id.asTypeOf(io.RobDeq.bits(1).id)
    io.RobDeq.bits(0).len             := TokenQueue.io.deq.bits.len.asTypeOf(io.RobDeq.bits(0).len)
    io.RobDeq.bits(1).len             := TokenQueue.io.deq.bits.len.asTypeOf(io.RobDeq.bits(1).len)
    io.RobDeq.bits(0).size            := TokenQueue.io.deq.bits.size.asTypeOf(io.RobDeq.bits(0).size)
    io.RobDeq.bits(1).size            := TokenQueue.io.deq.bits.size.asTypeOf(io.RobDeq.bits(1).size)
    io.RobDeq.bits(0).last            := false.B.asTypeOf(io.RobDeq.bits(0).last)//这里只适用于burst len == 1,待适配更多
    io.RobDeq.bits(1).last            := true.B.asTypeOf(io.RobDeq.bits(1).last)
    io.RobDeq.valid                   := RegNext(Mux(TokenQueue.io.deq.valid,ValidBitMap(TokenQueue.io.deq.bits.token) & io.RobDeq.ready ,false.B ))
    TokenQueue.io.deq.ready           := RegNext(ValidBitMap(TokenQueue.io.deq.bits.token) & io.RobDeq.ready) 
    io.ui_rio.ready                   := true.B

switch(Cat(io.ui_rio.fire, ValidBitMap(TokenQueue.io.deq.bits.token)&TokenQueue.io.deq.valid & io.RobDeq.ready )){
    is("b00".U){
        ValidBitMap                         :=  ValidBitMap}
    is("b01".U){
        ValidBitMap(TokenQueue.io.deq.bits.token)  :=  false.B}
    is("b10".U){
        ValidBitMap(io.ui_rio.bits.rtoken)  :=  true.B}
    is("b11".U){
        ValidBitMap(TokenQueue.io.deq.bits.token)  :=  false.B
        ValidBitMap(io.ui_rio.bits.rtoken)  :=  true.B}
} 
}