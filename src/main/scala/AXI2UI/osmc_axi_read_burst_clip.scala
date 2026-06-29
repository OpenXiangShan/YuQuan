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
import dataclass.data

class osmc_axi_read_burst_clip[T <: AXI2UI_PARAMETER](
    CLIP_PAPAMETER  :   T                                                              //AW FIFO DWPTH
)extends Module{


//local parameter define
val FIFO_WIDTH_ARR  =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFOARL1_PARAMETER.FIFO_WIDTH
val FIFO_WIDTH_ARW  =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFOARL2_PARAMETER.FIFO_WIDTH
val FIFO_WIDTH_RR   =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFORL1_PARAMETER.FIFO_WIDTH
val CMD_PARAM   =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFOCMD_PARAMETER
val CT_PARAM   =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFOCMD_TOKEN_PARAMETER

val AXI_BW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_BURSTW
val AXI_AW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_ADDRW
val AXI_LW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_LENW
val AXI_SW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_SIZEW
val AXI_QW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_QOSW

val AXI_DW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_DATAW
val AXI_TW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_STRBW


class CLIP_IO extends Bundle{
    val UnSplitCommandQueue  = Flipped(DecoupledIO(new UnSplitCommandQueueBundle))   
    val SplitCommandQueue    = DecoupledIO(new SplitCommandQueueBundle(AXI_PARAM.AXI_IDW))   
}
val io = IO(new CLIP_IO()) 

//burst state
val BURST_STATEW= 2
val BURST_IDLE  = 0.U
val BURST_FIRST = 1.U
val BURST_SECOND= 2.U
//burst type
val INCR    =   "b01".U
val WRAP    =   "b10".U
//burst clip
val BURST_SIZEW = 1 << AXI_SW
val BURST_CLIP_CMD_NUM_MAX = 16

val UI_DW                   =   CLIP_PAPAMETER.UI_PARAMETER.UI_DATAW//512bit
val UI_SW                   =   UI_DW/8 
val SPLICE_LEN              =   UI_DW / AXI_DW  
val SPLICE_LEN_W            =   log2Floor(SPLICE_LEN)                            //512bit
val CLIP_LEN_W              =   log2Floor(UI_DW)
val UI_ADDR_INCR            =   UI_DW/8 
val DATA_SPLICE_W           =   SPLICE_LEN_W + 1
val BURST_LW                =   AXI_LW + 1 
val BURST_BITSW             =   log2Ceil(AXI_DW * (1 << (AXI_LW)))
val BURST_BITS_INDEX        =   log2Floor(AXI_DW)
val CMD_EXTANDW             =   log2Ceil(AXI_DW * (1 << (AXI_LW)) / UI_DW)  + 1
val CMD_EXTAND_INDEX        =   log2Floor(UI_DW)
val TOKEN_FIFO_PARAM        =   new FIFO_PARAMETER(TOKEN_PARAM.TOKEN_WIDTH, 4, log2Floor(4))
val MergeNum                = UI_DW/AXI_DW
/****************************************************************************************************************************************************************/

/***************************************** cmd channel *****************************************/
val araddr                  = RegInit(0.U(AXI_AW.W))
val arburst                 = RegInit(0.U(AXI_BW.W))
val arlen                   = RegInit(0.U(AXI_LW.W))
val arsize                  = RegInit(0.U(AXI_SW.W))
val arqos                   = RegInit(0.U(AXI_QW.W))
val id                      = RegInit(0.U(AXI_PARAM.AXI_IDW.W))
//定义寄存器组来暂存一次AXI突发传输可能拆分的UI cmd地址最大数量为16
val burst_addr              =  RegInit(VecInit(Seq.fill(BURST_CLIP_CMD_NUM_MAX)(0.U(AXI_AW.W)))) 
val UiCmdNum                = WireInit(0.U(CMD_EXTANDW.W))
UiCmdNum                    := 1.U     //burst_bits-1 to avoid 512bit boundry; Round down signal exnum +1
//暂存AXI 读地址通道信息
araddr                      := Mux(io.UnSplitCommandQueue.fire, io.UnSplitCommandQueue.bits.addr , araddr)
arburst                     := Mux(io.UnSplitCommandQueue.fire, io.UnSplitCommandQueue.bits.burst , arburst)
arlen                       := Mux(io.UnSplitCommandQueue.fire, io.UnSplitCommandQueue.bits.len, arlen)
arsize                      := Mux(io.UnSplitCommandQueue.fire, io.UnSplitCommandQueue.bits.size, arsize)
arqos                       := Mux(io.UnSplitCommandQueue.fire, io.UnSplitCommandQueue.bits.qos, arqos)
id                          := Mux(io.UnSplitCommandQueue.fire, io.UnSplitCommandQueue.bits.id, id)



//ui cmd addr 在读aw fifo1时对所有的地址寄存器进行更新
(0 until BURST_CLIP_CMD_NUM_MAX).map(i => burst_addr(i) := Mux(io.UnSplitCommandQueue.fire,io.UnSplitCommandQueue.bits.addr ,burst_addr(i)))
//cmd counter
val ui_cmd_counter              = RegInit(0.U(CMD_EXTANDW.W))
val UnSplitCommandQueueStall    = RegInit(false.B)
when(UiCmdNum - 1.U === 0.U){
    UnSplitCommandQueueStall    := false.B
}.otherwise{
    UnSplitCommandQueueStall    := Mux(io.UnSplitCommandQueue.fire,true.B,Mux(ui_cmd_counter === UiCmdNum*MergeNum.U - 1.U & io.SplitCommandQueue.fire,false.B,UnSplitCommandQueueStall))
}
when(io.SplitCommandQueue.fire){
    when(ui_cmd_counter === UiCmdNum - 1.U){
        ui_cmd_counter := 0.U
    }.otherwise{
        ui_cmd_counter := ui_cmd_counter + 1.U
    }
}

//read AXI AR fifo
io.UnSplitCommandQueue.ready    := ui_cmd_counter === 0.U &  io.SplitCommandQueue.ready & (!UnSplitCommandQueueStall)

//ar fifo2 write
io.SplitCommandQueue.valid      :=  Mux(UiCmdNum - 1.U === 0.U , true.B , UnSplitCommandQueueStall) & io.UnSplitCommandQueue.valid
//写入的ui cmd addr 根据cmd_counter的当前值从地址寄存器组中取出
io.SplitCommandQueue.bits.addr  := Mux(io.UnSplitCommandQueue.fire,io.UnSplitCommandQueue.bits.addr,burst_addr(ui_cmd_counter/MergeNum.U))
io.SplitCommandQueue.bits.id    := Mux(io.UnSplitCommandQueue.fire,io.UnSplitCommandQueue.bits.id,id)
io.SplitCommandQueue.bits.len   := Mux(io.UnSplitCommandQueue.fire,io.UnSplitCommandQueue.bits.len,arlen)
io.SplitCommandQueue.bits.size  := Mux(io.UnSplitCommandQueue.fire,io.UnSplitCommandQueue.bits.size,arsize)
}