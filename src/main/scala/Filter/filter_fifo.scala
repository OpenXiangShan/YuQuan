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
import _root_.circt.stage.ChiselStage
import  OpenMc._




class filter_fifo[T <: Data](gen: T, getAddr: T => UInt, val depth : Int = 8)extends Module{
    private val ptr_w = log2Ceil(depth)
    val io = IO(new Bundle{
        val enq           = Flipped(Decoupled(gen))
        val deq           = Decoupled(gen)
        val conflictCheck = Flipped(Valid(UInt(BUNDLE_PARAM.ADDR_WIDTH.W)))
        val conflict      = Output(Bool())
        val empty         = Output(Bool())
        val full          = Output(Bool())
    })

    val fifo_buffer = Reg(Vec(depth, gen))
    val read_ptr    = RegInit(0.U((ptr_w + 1).W))
    val write_ptr   = RegInit(0.U((ptr_w + 1).W))
    val exist_flag  = RegInit(0.U(depth.W))

    val full_temp  = (read_ptr(ptr_w) =/= write_ptr(ptr_w)) && (read_ptr(ptr_w - 1, 0) === write_ptr(ptr_w - 1, 0))
    val empty_temp = read_ptr === write_ptr

    io.conflict := io.conflictCheck.valid && VecInit.tabulate(depth) { i =>
        exist_flag(i) && getAddr(fifo_buffer(i)) === io.conflictCheck.bits
    }.reduce(_ || _)

    io.enq.ready := !full_temp
    io.deq.valid := !empty_temp
    io.deq.bits  := fifo_buffer(read_ptr(ptr_w - 1, 0))

    val wcnt_flag = io.enq.fire
    when (wcnt_flag){
        write_ptr := write_ptr + 1.U
        fifo_buffer(write_ptr(ptr_w - 1, 0)) := io.enq.bits
        exist_flag := exist_flag | (1.U << write_ptr(ptr_w - 1, 0))
    }

    val rcnt_flag = io.deq.fire
    when (rcnt_flag){
        read_ptr := read_ptr + 1.U
        exist_flag := exist_flag & (~(1.U << read_ptr(ptr_w - 1, 0)))
    }
    when(wcnt_flag && rcnt_flag){
        exist_flag := (exist_flag & (~(1.U << read_ptr(ptr_w - 1, 0)))) | (1.U << write_ptr(ptr_w - 1, 0))
    }

    io.full  := full_temp
    io.empty := empty_temp
}