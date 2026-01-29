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



class osmc_axi_read_burst_clip[T <: AXI2UI_PARAMETER](
    CLIP_PAPAMETER  :   T                                                              //AW FIFO DWPTH
)extends Module{


//local parameter define
val FIFO_WIDTH_ARR  =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFOARL1_PARAMETER.FIFO_WIDTH
val FIFO_WIDTH_ARW  =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFOARL2_PARAMETER.FIFO_WIDTH
val FIFO_WIDTH_RR   =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFORL1_PARAMETER.FIFO_WIDTH
//val FIFO_WIDTH_RW   =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFORL2_PARAMETER.FIFO_WIDTH
val CMD_PARAM   =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFOCMD_PARAMETER
val CT_PARAM   =   CLIP_PAPAMETER.AXIRFIFO_PARAMETER.FIFOCMD_TOKEN_PARAMETER

val AXI_BW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_BURSTW
val AXI_AW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_ADDRW
val AXI_LW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_LENW
val AXI_SW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_SIZEW
val AXI_QW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_QOSW

val AXI_DW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_DATAW
val AXI_TW      =   CLIP_PAPAMETER.AXI_PARAMETER.AXI_STRBW

//IO define
class TOKEN_COUNTIO_R extends Bundle{
    val token_aren      =   Output(Bool())
    val token_arready   =   Output(Bool())
}
class CLIP_IO extends Bundle{
    val fifol1_arrio  = Flipped(new FIFO_RIO(FIFO_WIDTH_ARR))   //connect fifo L1
    val fifol2_arwio  = Flipped(new FIFO_WIO(FIFO_WIDTH_ARW))   //connect fifo L2
    val fifol1_rwio   = Flipped(new FIFO_WIO(FIFO_WIDTH_RR))    //connect fifo L1
    val cmd_fifo_wio  = Flipped(new FIFO_WIO(CMD_PARAM.FIFO_WIDTH))
    val cmd_fifo_rio  = Flipped(new FIFO_RIO(CMD_PARAM.FIFO_WIDTH))
    val token_fifo_wio= Flipped(new FIFO_WIO(CT_PARAM.FIFO_WIDTH))
    val rrob_io       = Flipped(Decoupled(UInt(CLIP_PAPAMETER.UI_PARAMETER.UI_DATAW.W)))
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

val UI_DW   =   CLIP_PAPAMETER.UI_PARAMETER.UI_DATAW//512bit
val UI_SW   =   UI_DW/8 

val SPLICE_LEN      =   UI_DW / AXI_DW  
val SPLICE_LEN_W    =   log2Floor(SPLICE_LEN)                            //512bit
val CLIP_LEN_W      =   log2Floor(UI_DW)
val UI_ADDR_INCR    =   UI_DW/8 
val DATA_SPLICE_W   =   SPLICE_LEN_W + 1

val BURST_LW    =   AXI_LW + 1 
val BURST_BITSW =   log2Ceil(AXI_DW * (1 << (AXI_LW)))
val BURST_BITS_INDEX    =   log2Floor(AXI_DW)
val CMD_EXTANDW =   log2Ceil(AXI_DW * (1 << (AXI_LW)) / UI_DW)  + 1
val CMD_EXTAND_INDEX    =   log2Floor(UI_DW)

val TOKEN_FIFO_PARAM    =   new FIFO_PARAMETER(TOKEN_PARAM.TOKEN_WIDTH, 4, log2Floor(4))
val MergeNum = UI_DW/AXI_DW
/****************************************************************************************************************************************************************/

/***************************************** cmd channel *****************************************/
val araddr  = RegInit(0.U(AXI_AW.W))
val arburst = RegInit(0.U(AXI_BW.W))
val arlen   = RegInit(0.U(AXI_LW.W))
val arsize  = RegInit(0.U(AXI_SW.W))
val arqos   = RegInit(0.U(AXI_QW.W))
//定义寄存器组来暂存一次AXI突发传输可能拆分的UI cmd地址最大数量为16
val burst_addr     =  RegInit(VecInit(Seq.fill(BURST_CLIP_CMD_NUM_MAX)(0.U(AXI_AW.W)))) 
//计算一次AXI突发CMD会拆分为几个UI CMD
val burst_bits  = Wire(UInt(BURST_BITSW.W))
val UiCmdNum    = Wire(UInt(CMD_EXTANDW.W))
burst_bits  :=  (arlen+1.U) << BURST_BITS_INDEX
UiCmdNum   :=  Cat(0.U, ((burst_bits-1.U)  >> CMD_EXTAND_INDEX)) + 1.U    //burst_bits-1 to avoid 512bit boundry; Round down signal exnum +1

//暂存AXI 读地址通道信息
araddr  := Mux(io.fifol1_arrio.ren, io.fifol1_arrio.rdata(AXI_SW+AXI_QW+AXI_LW+AXI_BW+AXI_AW-1, AXI_SW+AXI_QW+AXI_LW+AXI_BW), araddr)
arburst := Mux(io.fifol1_arrio.ren, io.fifol1_arrio.rdata(AXI_SW+AXI_QW+AXI_LW+AXI_BW-1, AXI_SW+AXI_QW+AXI_LW), arburst)
arlen   := Mux(io.fifol1_arrio.ren, io.fifol1_arrio.rdata(AXI_SW+AXI_QW+AXI_LW-1, AXI_SW+AXI_QW), arlen)
arsize  := Mux(io.fifol1_arrio.ren, io.fifol1_arrio.rdata(AXI_SW+AXI_QW-1, AXI_QW), arsize)
arqos   := Mux(io.fifol1_arrio.ren, io.fifol1_arrio.rdata(AXI_QW-1, 0), arqos)
//artoken wen


//ui cmd addr 在读aw fifo1时对所有的地址寄存器进行更新
(0 until BURST_CLIP_CMD_NUM_MAX).map(i => burst_addr(i) := Mux(io.fifol1_arrio.ren,io.fifol1_arrio.rdata(AXI_SW+AXI_QW+AXI_LW+AXI_BW+AXI_AW-1, AXI_SW+AXI_QW+AXI_LW+AXI_BW) +(i.U<<6) ,burst_addr(i)))
//cmd counter
val ui_cmd_counter = RegInit(0.U(CMD_EXTANDW.W))
val  cmd_counter_add_cond    = WireInit(false.B)
val ui_cmd_counter_en = RegInit(false.B)
ui_cmd_counter_en := Mux(ui_cmd_counter_en,Mux(io.fifol2_arwio.wen,false.B,ui_cmd_counter_en),Mux(io.fifol1_arrio.ren,true.B,ui_cmd_counter_en))
cmd_counter_add_cond        := ui_cmd_counter_en | io.fifol1_arrio.ren
val  cmd_counter_reset_cond  = WireInit(false.B)
cmd_counter_reset_cond      := (ui_cmd_counter === UiCmdNum*MergeNum.U - 1.U) & cmd_counter_add_cond
ui_cmd_counter              := Mux(cmd_counter_reset_cond,0.U,ui_cmd_counter + cmd_counter_add_cond)
//token wen
val readToken = RegInit(0.U(TOKEN_PARAM.TOKEN_WIDTH.W))
val token_aren = WireInit(false.B)
val token_arready = WireInit(false.B)
token_aren := (~io.fifol2_arwio.full) & ((ui_cmd_counter%MergeNum.U) === 0.U)&cmd_counter_add_cond & ~io.token_fifo_wio.full
token_arready := ~io.fifol2_arwio.full
readToken := readToken + (token_aren&token_arready)
//read AXI AR fifo
io.fifol1_arrio.ren   := ui_cmd_counter === 0.U &  ~io.fifol2_arwio.full &(~io.fifol1_arrio.empty)&(~ui_cmd_counter_en) & ~io.cmd_fifo_wio.full & ~io.token_fifo_wio.full

//ar fifo2 write
io.fifol2_arwio.wen   := (~io.fifol2_arwio.full) & (((ui_cmd_counter+1.U)%MergeNum.U) === 0.U)&(ui_cmd_counter_en) & ~io.token_fifo_wio.full
//写入的ui cmd addr 根据cmd_counter的当前值从地址寄存器组中取出
io.fifol2_arwio.wdata := Cat(readToken,burst_addr(ui_cmd_counter/MergeNum.U))

//cmd buffer write
val   delay1    = RegNext(io.fifol1_arrio.ren)
val   delay2    = RegNext(delay1)
    io.cmd_fifo_wio.wen     :=  delay2    // reg cmd_load 
    io.cmd_fifo_wio.wdata   :=  Cat(arlen,arsize)
    io.token_fifo_wio.wen   :=  io.fifol2_arwio.wen
    io.token_fifo_wio.wdata :=  readToken

/***************************************** data channel *****************************************/
val data_out    =   Wire(UInt(AXI_DW.W))
val data_last   =   RegInit(false.B)
val data_reg    =   RegInit(VecInit.fill(SPLICE_LEN)(0.U(AXI_DW.W)))

val rdata_vec   =   Wire(Vec(SPLICE_LEN, UInt(AXI_DW.W)))
val burst_len   =   Wire(UInt(BURST_LW.W))
val burst_size  =   RegInit(0.U(AXI_SW.W)) 
val burst_counter   =   RegInit(0.U(BURST_LW.W)) 
val burst_decond    =   Wire(Bool())
val burst_counter_nxt    =  Wire(UInt(BURST_LW.W))
val burst_counter_init   =  Wire(UInt(BURST_LW.W))


//burst counter
    io.cmd_fifo_rio.ren :=  (burst_counter   === 0.U) & ~io.cmd_fifo_rio.empty

    for(i <- 0 until SPLICE_LEN){
        rdata_vec(i)    :=  io.rrob_io.bits((i+1)*AXI_DW-1, i*(AXI_DW))
    }
    data_reg    :=  Mux(io.rrob_io.valid, rdata_vec, data_reg)
    burst_len   :=  Cat(0.U, io.cmd_fifo_rio.rdata(AXI_LW + AXI_SW - 1, AXI_SW)) 
    burst_size  :=  Mux(io.cmd_fifo_rio.ren, io.cmd_fifo_rio.rdata(AXI_SW - 1, 0), burst_size)

    burst_decond    :=  io.fifol1_rwio.wen
    burst_counter_nxt   :=  Mux(burst_decond, Mux(burst_counter === 0.U, 0.U, burst_counter - 1.U), burst_counter)
    burst_counter_init  :=  burst_len
    burst_counter   :=  Mux(io.cmd_fifo_rio.ren, burst_counter_init, burst_counter_nxt)

//data counter

    when(io.rrob_io.fire){
        data_out := io.rrob_io.bits(AXI_DW-1, 0)
    }.elsewhen(RegNext(io.rrob_io.fire)){
        data_out := io.rrob_io.bits(UI_DW -1 ,AXI_DW)
    }.otherwise{
        data_out := 0.U
    }
    data_last := Mux(io.rrob_io.fire | RegNext(io.rrob_io.fire),~data_last,data_last)
//output
    io.rrob_io.ready   :=  ~io.fifol1_rwio.full & (~data_last)
    io.fifol1_rwio.wen  :=  RegNext(io.rrob_io.fire) | io.rrob_io.fire 
    io.fifol1_rwio.wdata:=  Cat(data_last, data_out)


}