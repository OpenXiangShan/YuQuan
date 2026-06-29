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
import chisel3.experimental.FlatIO
import chisel3.util._
import BUNDLE_PARAM._

class offset_odt_delay extends  Module{
    val io = IO(new Bundle {
        val write_vld_phase0            = Flipped(Bool())
        val read_vld_phase0             = Flipped(Bool())
        val write_vld_phase1            = Flipped(Bool())
        val read_vld_phase1             = Flipped(Bool())
        val wr_odt_delay                = Flipped(UInt(PARAMETERWIDTH.W))
        val wr_odt_hold                 = Flipped(UInt(PARAMETERWIDTH.W))
        val rd_odt_delay                = Flipped(UInt(PARAMETERWIDTH.W))
        val rd_odt_hold                 = Flipped(UInt(PARAMETERWIDTH.W)) 
        val odt_out                     = UInt((1<<BUNDLE_PARAM.ODTBITS).W)
    })
    val wr_offset_type = WireInit(false.B)
    val rd_offset_type = WireInit(false.B)
    val wr_shift_reg_0 = RegInit(VecInit(Seq.fill(1<<PARAMETERWIDTH)(0.U)))
    val wr_shift_reg_1 = RegInit(VecInit(Seq.fill(1<<PARAMETERWIDTH)(0.U)))
    val rd_shift_reg_0 = RegInit(VecInit(Seq.fill(1<<PARAMETERWIDTH)(0.U)))
    val rd_shift_reg_1 = RegInit(VecInit(Seq.fill(1<<PARAMETERWIDTH)(0.U)))
    when(io.wr_odt_delay(0) === 1.U){
        wr_offset_type := MuxCase(false.B,Seq(
                            (io.write_vld_phase0,true.B),
                            (io.write_vld_phase1,false.B)
        ))
    }.otherwise{
        wr_offset_type := MuxCase(false.B,Seq(
                            (io.write_vld_phase0,false.B),
                            (io.write_vld_phase1,true.B)
        ))
    }
    when(io.rd_odt_delay(0) === 1.U){
        rd_offset_type := MuxCase(false.B,Seq(
                            (io.read_vld_phase0,true.B),
                            (io.read_vld_phase1,false.B)
        ))
    }.otherwise{
        rd_offset_type := MuxCase(false.B,Seq(
                            (io.read_vld_phase0,false.B),
                            (io.read_vld_phase1,true.B)
        ))
    }
//load write odt
when(io.write_vld_phase0 | io.write_vld_phase1){
    when(wr_offset_type){ //需要偏移
        when(io.wr_odt_hold(0)===1.U){
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                wr_shift_reg_0(i) := Mux(i.U>=1.U&&i.U<((io.wr_odt_hold+1.U)>>1),1.U,0.U)
            )
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                wr_shift_reg_1(i) := Mux(i.U>=0.U&&i.U<((io.wr_odt_hold+1.U)>>1),1.U,0.U)
            )
        }.otherwise{
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                wr_shift_reg_0(i) := Mux(i.U>=1.U&&i.U<=(io.wr_odt_hold>>1),1.U,0.U)
            )
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                wr_shift_reg_1(i) := Mux(i.U>=0.U&&i.U<(io.wr_odt_hold>>1),1.U,0.U)
            )
        }
    }.otherwise{
        when(io.wr_odt_hold(0)===1.U){
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                wr_shift_reg_0(i) := Mux(i.U>=0.U&&i.U<=(io.wr_odt_hold>>1),1.U,0.U)
            )
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                wr_shift_reg_1(i) := Mux(i.U>=0.U&&i.U<(io.wr_odt_hold>>1),1.U,0.U)
            )
        }.otherwise{
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                wr_shift_reg_0(i) := Mux(i.U>=0.U&&i.U<(io.wr_odt_hold>>1),1.U,0.U)
            )
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                wr_shift_reg_1(i) := Mux(i.U>=0.U&&i.U<(io.wr_odt_hold>>1),1.U,0.U)
            )
        }
    }
}.otherwise{
    (0 until((1<<PARAMETERWIDTH)-1)).map(i=> wr_shift_reg_0(i) := wr_shift_reg_0(i+1))
    (0 until((1<<PARAMETERWIDTH)-1)).map(i=> wr_shift_reg_1(i) := wr_shift_reg_1(i+1))
}
val wr_offset = WireInit(0.U((1<<BUNDLE_PARAM.ODTBITS).W))
val wr_delay  = RegInit(VecInit(Seq.fill(1<<PARAMETERWIDTH)(0.U((1<<BUNDLE_PARAM.ODTBITS).W))))
val wr_odt    = WireInit(0.U(((1<<BUNDLE_PARAM.ODTBITS).W)))
// dontTouch(wr_odt)
wr_offset := Cat(wr_shift_reg_1(0),wr_shift_reg_0(0))
wr_delay(0) := wr_offset
(1 until(1<<PARAMETERWIDTH)).map(i => 
    wr_delay(i) := wr_delay(i-1)
)
wr_odt := wr_delay((io.wr_odt_delay>>1)-2.U)
 
//load read odt
when(io.read_vld_phase0 | io.read_vld_phase1){
    when(rd_offset_type){ //需要偏移
        when(io.rd_odt_hold(0)===1.U){
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                rd_shift_reg_0(i) := Mux(i.U>=1.U&&i.U<((io.rd_odt_hold+1.U)>>1),1.U,0.U)
            )
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                rd_shift_reg_1(i) := Mux(i.U>=0.U&&i.U<((io.rd_odt_hold+1.U)>>1),1.U,0.U)
            )
        }.otherwise{
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                rd_shift_reg_0(i) := Mux(i.U>=1.U&&i.U<=(io.rd_odt_hold>>1),1.U,0.U)
            )
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                rd_shift_reg_1(i) := Mux(i.U>=0.U&&i.U<(io.rd_odt_hold>>1),1.U,0.U)
            )
        }
    }.otherwise{
        when(io.rd_odt_hold(0)===1.U){
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                rd_shift_reg_0(i) := Mux(i.U>=0.U&&i.U<=(io.rd_odt_hold>>1),1.U,0.U)
            )
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                rd_shift_reg_1(i) := Mux(i.U>=0.U&&i.U<(io.rd_odt_hold>>1),1.U,0.U)
            )
        }.otherwise{
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                rd_shift_reg_0(i) := Mux(i.U>=0.U&&i.U<(io.rd_odt_hold>>1),1.U,0.U)
            )
            (0 until(1<<PARAMETERWIDTH)).map(i =>
                rd_shift_reg_1(i) := Mux(i.U>=0.U&&i.U<(io.rd_odt_hold>>1),1.U,0.U)
            )
        }
    }
}.otherwise{
    (0 until((1<<PARAMETERWIDTH)-1)).map(i=> rd_shift_reg_0(i) := rd_shift_reg_0(i+1))
    (0 until((1<<PARAMETERWIDTH)-1)).map(i=> rd_shift_reg_1(i) := rd_shift_reg_1(i+1))
}
val rd_offset = WireInit(0.U((1<<BUNDLE_PARAM.ODTBITS).W))
val rd_delay  = RegInit(VecInit(Seq.fill(1<<PARAMETERWIDTH)(0.U((1<<BUNDLE_PARAM.ODTBITS).W))))
val rd_odt    = WireInit(0.U(((1<<BUNDLE_PARAM.ODTBITS).W)))
rd_offset := Cat(rd_shift_reg_1(0),rd_shift_reg_0(0))
rd_delay(0) := rd_offset
(1 until(1<<PARAMETERWIDTH)).map(i => 
    rd_delay(i) := rd_delay(i-1)
)
rd_odt := rd_delay((io.rd_odt_delay>>1)-2.U)

io.odt_out := Cat(wr_odt(1)|rd_odt(1),wr_odt(0)|rd_odt(0))
}