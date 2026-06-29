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

class osmc_axi_write_burst_clip[T <: AXI2UI_PARAMETER](
    CLIP_PAPAMETER  :   T                                                              //AW FIFO DWPTH
)extends Module{


val AXI_BW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_BURSTW
val AXI_AW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_ADDRW
val AXI_LW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_LENW
val AXI_SW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_SIZEW
val AXI_QW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_QOSW

val AXI_DW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_DATAW
val AXI_TW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_STRBW

//IO define
class TOKEN_COUNTIO_W extends Bundle{
    val token_awen      =   Output(Bool())
    val token_awready   =   Output(Bool()) 
   // val token_wvalid    =   Output(Bool())
}
class CLIP_IO extends Bundle{
    val awIn      = Flipped(Decoupled(new AXIWriteAddrInfo))
    val cmdOut    = Decoupled(new CMDIO())
    val wIn       = Flipped(Decoupled(new AXIWriteDataInfo))
    val dataOut   = Decoupled(new WrDataIO())
    // val token_inio      = Flipped(new TOKEN_IO())
    // val token_countio   = new TOKEN_COUNTIO_W()
}
val io = IO(new CLIP_IO()) 

//parameter

//burst type
val INCR    =   BigInt("01", 2).U
val WRAP    =   BigInt("10", 2).U
//burst clip
val BURST_SIZEW = 1 << AXI_SW

val BURST_CLIP_CMD_NUM_MAX = 16

val UI_DW   =   CLIP_PAPAMETER.UI_PARAMETER.UI_DATAW//512bit
val UI_SW   =   UI_DW/8 

val SPLICE_LEN      =   UI_DW / AXI_DW  
val SPLICE_LEN_W    =   log2Floor(SPLICE_LEN)                            //512bit
val CLIP_LEN_W      =   log2Floor(UI_DW)
val UI_ADDR_INCR    =   UI_DW/8 
val DATA_SPLICE_W   =   SPLICE_LEN_W + 1

val BURST_BITSW =   log2Floor(AXI_DW * (1 << (AXI_LW)))
val BURST_BITS_INDEX    =   log2Floor(AXI_DW)
val CMD_EXTANDW =   log2Floor(AXI_DW * (1 << (AXI_LW)) / UI_DW) + 1 
val CMD_EXTAND_INDEX    =   log2Floor(UI_DW)

///
val awaddr  = RegInit(0.U(AXI_AW.W))
val awburst = RegInit(0.U(AXI_BW.W))
val awlen   = RegInit(0.U(AXI_LW.W))
val awsize  = RegInit(0.U(AXI_SW.W))
val awqos   = RegInit(0.U(AXI_QW.W))
//定义寄存器组来暂存一次AXI突发传输可能拆分的UI cmd地址最大数量为16
val burst_addr     =  RegInit(VecInit(Seq.fill(BURST_CLIP_CMD_NUM_MAX)(0.U(AXI_AW.W)))) 
//计算一次AXI突发CMD会拆分为几个UI CMD
val burst_bits  = Wire(UInt(BURST_BITSW.W))
val UiCmdNum    = Wire(UInt(CMD_EXTANDW.W))
burst_bits  :=  (awlen+1.U) << BURST_BITS_INDEX
UiCmdNum   :=  Cat(0.U, ((burst_bits-1.U)  >> CMD_EXTAND_INDEX)) + 1.U    //burst_bits-1 to avoid 512bit boundry; Round down signal exnum +1
//计算需要几个AXI data拼接为一个UI data 
val MergeNum = UI_DW/AXI_DW
//暂存AXI 写地址通道信息
awaddr  := Mux(io.awIn.fire, io.awIn.bits.addr, awaddr)
awburst := Mux(io.awIn.fire, io.awIn.bits.burst, awburst)
awlen   := Mux(io.awIn.fire, io.awIn.bits.len, awlen)
awsize  := Mux(io.awIn.fire, io.awIn.bits.size, awsize)
awqos   := Mux(io.awIn.fire, io.awIn.bits.qos, awqos)
//ui cmd addr 在读aw fifo1时对所有的地址寄存器进行更新
(0 until BURST_CLIP_CMD_NUM_MAX).map(i => burst_addr(i) := Mux(io.awIn.fire,io.awIn.bits.addr +(i.U<<6) ,burst_addr(i)))
//cmd counter
val ui_cmd_counter = RegInit(0.U(CMD_EXTANDW.W))
val ui_cmd_counter_en = RegInit(false.B)
ui_cmd_counter_en := Mux(ui_cmd_counter_en,Mux(io.cmdOut.fire,false.B,ui_cmd_counter_en),Mux(io.awIn.fire,true.B,ui_cmd_counter_en))
val  cmd_counter_add_cond    = WireInit(false.B)
cmd_counter_add_cond        := ui_cmd_counter_en | io.awIn.fire
val  cmd_counter_reset_cond  = WireInit(false.B)
cmd_counter_reset_cond      := (ui_cmd_counter === UiCmdNum*MergeNum.U - 1.U) & cmd_counter_add_cond
ui_cmd_counter              := Mux(cmd_counter_reset_cond,0.U,ui_cmd_counter + cmd_counter_add_cond)


//read AXI AW fifo
io.awIn.ready   := ui_cmd_counter === 0.U & io.cmdOut.ready & (~ui_cmd_counter_en)

//aw fifo2 write
io.cmdOut.valid := (((ui_cmd_counter+1.U)%MergeNum.U) === 0.U)&ui_cmd_counter_en
//写入的ui cmd addr 根据cmd_counter的当前值从地址寄存器组中取出
io.cmdOut.bits.addr := burst_addr(ui_cmd_counter/MergeNum.U)
io.cmdOut.bits.token := 0.U
io.cmdOut.bits.pri := 0.U

/***************************************** data channel *****************************************/
val data_counter           = RegInit(0.U(DATA_SPLICE_W.W))
val UI_data                = RegInit(VecInit(Seq.fill(MergeNum)(0.U(AXI_DW.W))))
val UI_data_mask           = RegInit(VecInit(Seq.fill(MergeNum)(0.U(AXI_TW.W))))
val data_counter_add_cond  = RegInit(false.B)
 data_counter_add_cond    := io.wIn.fire
val data_counter_reset_cond = WireInit(false.B)
data_counter_reset_cond   := (data_counter === MergeNum.U - 1.U) & data_counter_add_cond
data_counter              := Mux(data_counter_reset_cond,0.U,data_counter + data_counter_add_cond)
//记录当前AXI传输的突发长度是否为奇数，如果是奇数需要在最后一个合并的数据补上半个数据字的空
val MergeLast   = WireInit(0.U)
MergeLast      := (awlen+1.U)(0) //这个标志位还是会在读aw fifo1时更新
val mask_slice_num  = WireInit(0.U(AXI_LW.W))
mask_slice_num := MergeNum.U - ((awlen+1.U)/MergeNum.U) //awlen >=1 
//read w fifo1 and load UI data
val w_ren_flag      = RegInit(false.B)
w_ren_flag          := Mux(w_ren_flag ,true.B,Mux(io.awIn.fire,true.B,w_ren_flag) )
io.wIn.ready        := io.dataOut.ready & w_ren_flag


when(MergeLast === 1.U){ //需要补空
    when((data_counter >= mask_slice_num).asBool){
        UI_data(data_counter) := Fill(AXI_DW,0.U)
        UI_data_mask(data_counter) := Fill(AXI_TW,0.U)
    }
}.otherwise{
    UI_data(0)     := Mux(io.wIn.fire,io.wIn.bits.data,UI_data(0))
    UI_data_mask(0):= Mux(io.wIn.fire,Fill(AXI_TW,1.U),UI_data_mask(0)) 
    (1 until MergeNum).map(i =>UI_data(i) := Mux(io.wIn.fire , UI_data(i-1),UI_data(i))  )
    (1 until MergeNum).map(i =>UI_data_mask(i) := Mux(io.wIn.fire , UI_data_mask(i-1),UI_data_mask(i))  )
}
//w fifo2 write
io.dataOut.valid := data_counter_reset_cond
io.dataOut.bits.wdata := Cat(UI_data)
io.dataOut.bits.wstrb := Cat(UI_data_mask)
//


}