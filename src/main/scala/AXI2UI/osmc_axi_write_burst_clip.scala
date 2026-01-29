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


class osmc_axi_write_burst_clip[T <: AXI2UI_PARAMETER](
    CLIP_PAPAMETER  :   T                                                              //AW FIFO DWPTH
)extends Module{


//local parameter define
val FIFO_WIDTH_AWR  =   CLIP_PAPAMETER.AXIWFIFO_PARAMETER.FIFOAWL1_PARAMETER.FIFO_WIDTH
val FIFO_WIDTH_AWW  =   CLIP_PAPAMETER.AXIWFIFO_PARAMETER.FIFOAWL2_PARAMETER.FIFO_WIDTH
val FIFO_WIDTH_WR   =   CLIP_PAPAMETER.AXIWFIFO_PARAMETER.FIFOWL1_PARAMETER.FIFO_WIDTH
val FIFO_WIDTH_WW   =   CLIP_PAPAMETER.AXIWFIFO_PARAMETER.FIFOWL2_PARAMETER.FIFO_WIDTH

val AXI_BW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_BURSTW
val AXI_AW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_ADDRW
val AXI_LW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_LENW
val AXI_SW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_SIZEW
val AXI_QW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_QOSW

val AXI_DW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_DATAW
val AXI_TW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_STRBW

//IO define
class CLIP_IO extends Bundle{
    val fifol1_awrio    = Flipped(new FIFO_RIO(FIFO_WIDTH_AWR))   //connect fifo L1
    val fifol2_awwio    = Flipped(new FIFO_WIO(FIFO_WIDTH_AWW))   //connect fifo L2
    val fifol1_wrio     = Flipped(new FIFO_RIO(FIFO_WIDTH_WR))    //connect fifo L1
    val fifol2_wwio     = Flipped(new FIFO_WIO(FIFO_WIDTH_WW))    //connect fifo L2
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

val TOKEN_FIFO_PARAM    =   new FIFO_PARAMETER(TOKEN_PARAM.TOKEN_WIDTH, 4, log2Floor(4))

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

awaddr  := Mux(io.fifol1_awrio.ren, io.fifol1_awrio.rdata(AXI_SW+AXI_QW+AXI_LW+AXI_BW+AXI_AW-1, AXI_SW+AXI_QW+AXI_LW+AXI_BW), awaddr)
awburst := Mux(io.fifol1_awrio.ren, io.fifol1_awrio.rdata(AXI_SW+AXI_QW+AXI_LW+AXI_BW-1, AXI_SW+AXI_QW+AXI_LW), awburst)
awlen   := Mux(io.fifol1_awrio.ren, io.fifol1_awrio.rdata(AXI_SW+AXI_QW+AXI_LW-1, AXI_SW+AXI_QW), awlen)
awsize  := Mux(io.fifol1_awrio.ren, io.fifol1_awrio.rdata(AXI_SW+AXI_QW-1, AXI_QW), awsize)
awqos   := Mux(io.fifol1_awrio.ren, io.fifol1_awrio.rdata(AXI_QW-1, 0), awqos)
//ui cmd addr 在读aw fifo1时对所有的地址寄存器进行更新
(0 until BURST_CLIP_CMD_NUM_MAX).map(i => burst_addr(i) := Mux(io.fifol1_awrio.ren,io.fifol1_awrio.rdata(AXI_SW+AXI_QW+AXI_LW+AXI_BW+AXI_AW-1, AXI_SW+AXI_QW+AXI_LW+AXI_BW) +(i.U<<6) ,burst_addr(i)))
//cmd counter
val ui_cmd_counter = RegInit(0.U(CMD_EXTANDW.W))
val ui_cmd_counter_en = RegInit(false.B)
ui_cmd_counter_en := Mux(ui_cmd_counter_en,Mux(io.fifol2_awwio.wen,false.B,ui_cmd_counter_en),Mux(io.fifol1_awrio.ren,true.B,ui_cmd_counter_en))
val  cmd_counter_add_cond    = WireInit(false.B)
cmd_counter_add_cond        := ui_cmd_counter_en |io.fifol1_awrio.ren
val  cmd_counter_reset_cond  = WireInit(false.B)
cmd_counter_reset_cond      := (ui_cmd_counter === UiCmdNum*MergeNum.U - 1.U) & cmd_counter_add_cond
ui_cmd_counter              := Mux(cmd_counter_reset_cond,0.U,ui_cmd_counter + cmd_counter_add_cond)
//read AXI AW fifo
io.fifol1_awrio.ren   := ui_cmd_counter === 0.U &  ~io.fifol2_awwio.full & (~io.fifol1_awrio.empty)&(~ui_cmd_counter_en)
//aw fifo2 write
io.fifol2_awwio.wen   := (~io.fifol2_awwio.full) & (((ui_cmd_counter+1.U)%MergeNum.U) === 0.U)&ui_cmd_counter_en
//写入的ui cmd addr 根据cmd_counter的当前值从地址寄存器组中取出
io.fifol2_awwio.wdata := Cat(0.U,burst_addr(ui_cmd_counter/MergeNum.U))

/***************************************** data channel *****************************************/
val data_counter           = RegInit(0.U(DATA_SPLICE_W.W))
val UI_data                = RegInit(VecInit(Seq.fill(MergeNum)(0.U(AXI_DW.W))))
val UI_data_mask           = RegInit(VecInit(Seq.fill(MergeNum)(0.U(AXI_TW.W))))
val data_counter_add_cond  = RegInit(false.B)
 data_counter_add_cond    := io.fifol1_wrio.ren
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
w_ren_flag          := Mux(w_ren_flag ,true.B,Mux(io.fifol1_awrio.ren,true.B,w_ren_flag) )
io.fifol1_wrio.ren        := (~io.fifol1_wrio.empty) & (~io.fifol2_wwio.full)&w_ren_flag


when(MergeLast === 1.U){ //需要补空
    when((data_counter >= mask_slice_num).asBool){
        UI_data(data_counter) := Fill(AXI_DW,0.U)
        UI_data_mask(data_counter) := Fill(AXI_TW,0.U)
    }
}.otherwise{
    UI_data(0)     := Mux(io.fifol1_wrio.ren,io.fifol1_wrio.rdata(AXI_TW + AXI_DW, AXI_TW + 1),UI_data(0))
    UI_data_mask(0):= Mux(io.fifol1_wrio.ren,Fill(AXI_TW,1.U),UI_data_mask(0)) 
    (1 until MergeNum).map(i =>UI_data(i) := Mux(io.fifol1_wrio.ren , UI_data(i-1),UI_data(i))  )
    (1 until MergeNum).map(i =>UI_data_mask(i) := Mux(io.fifol1_wrio.ren , UI_data_mask(i-1),UI_data_mask(i))  )
}
//w fifo2 write
io.fifol2_wwio.wen := data_counter_reset_cond
io.fifol2_wwio.wdata := Cat(Cat(UI_data_mask),Cat(UI_data))
//


}