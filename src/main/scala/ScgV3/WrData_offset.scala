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

import  chisel3._
import  chisel3.experimental.FlatIO
import  chisel3.util._
import  BUNDLE_PARAM._

class WrData_offset(width : Int) extends Module{
    val  io  =IO(new Bundle{
        val  vld            = Flipped(Bool())
        val  write_phase0   = Flipped(Bool())
        val  write_phase1   = Flipped(Bool())
        val  write_latency  = Flipped(UInt(BUNDLE_PARAM.McParamWidth.W))
        val  write_data     = Flipped(UInt((width<<1).W))
        val  offset_data    = UInt(width.W)
        val  dfi_parameter_mode = Flipped(Bool())
    })
    val vld_delay    = VecInit(Seq.fill(3)(false.B))
        vld_delay(0) := RegNext(io.vld)
        vld_delay(1) := RegNext(vld_delay(0))
        vld_delay(2) := RegNext(vld_delay(1))
    val offset_type  = WireInit(false.B)
    val offset_data   = WireInit(0.U(width.W))
    //offset_type 计算
    when(io.write_latency(0) === 0.U){  //WL为偶数
        when(io.write_phase1 ){
                offset_type := 1.U 
        }.elsewhen(io.write_phase0 ){
                offset_type := 0.U
        }
    }.otherwise{   //WL为奇数
        when(io.write_phase1){
                offset_type := 0.U 
        }.elsewhen(io.write_phase0 ){
                offset_type := 1.U
        }
    }
    val shift_Reg  = RegInit(VecInit(Seq.fill(3)(0.U(width.W))))
    //load data
    when(io.vld){
        when(offset_type && io.dfi_parameter_mode){ // 需要偏移
            shift_Reg := Cat(shift_Reg(2)(width-1,width>>1),io.write_data,shift_Reg(0)(((width>>1)-1),0)).asTypeOf(shift_Reg)
        }.otherwise{
            shift_Reg := Cat(shift_Reg(2),io.write_data).asTypeOf(shift_Reg)
        }
    }.otherwise{
        shift_Reg     := Cat(0.U,shift_Reg(2),shift_Reg(1)).asTypeOf(shift_Reg)
    }
    offset_data        := Mux(vld_delay.asUInt.orR,shift_Reg(0),0.U)
    //打整拍处理
    val WrDataDelay        = RegInit(VecInit.fill(Wlmax)(0.U(DATABITS.W)))
    for(i <- 1  until  Wlmax){
            WrDataDelay(0) := offset_data
            WrDataDelay(i) := (WrDataDelay(i-1))
    }
    io.offset_data := WrDataDelay((io.write_latency>>1)-3.U) //打整数拍
}