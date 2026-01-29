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




class filter_fifo(val w : Int , val depth : Int , val ptr_w : Int ,  val p1 : Int , val p2 : Int)extends Module{
    val io = IO(new Bundle{
    val data_i        = Input(UInt(w.W))
    val read_en       = Input(UInt(1.W))
    val write_en      = Input(UInt(1.W))
    
    val wconflict_data = Input(UInt(w.W))
    val wconflict_en   = Input(Bool())
    val conflict       = Output(Bool()) 
    
    val data_o       = Output(UInt(w.W)) 
    val empty        = Output(UInt(1.W)) 
    val full         = Output(UInt(1.W)) 
    })
    val fifo_buffer       = Reg(Vec(depth, UInt(w.W))) //Mem(l, UInt(w.W))
    
    val read_ptr          = RegInit(0.U((ptr_w + 1).W))
    val write_ptr         = RegInit(0.U((ptr_w + 1).W))
    val exist_flag        = RegInit(0.U(depth.W))

    val empty_temp          =  Wire(UInt(1.W))
    val ready_temp          =  RegInit(true.B)
    val full_temp           =  Wire(UInt(1.W))
    val wcnt_flag           =  Wire(Bool())
    val rcnt_flag           =  Wire(Bool())

    /******************************************************************************************************************************/
    io.conflict := io.wconflict_en && VecInit.tabulate(depth) {i => 
        exist_flag(i) && fifo_buffer(i)(p1, p2) === io.wconflict_data(p1, p2)
    }.reduce(_ || _)
    /******************************************************************************************************************************/

    full_temp  := ((read_ptr(ptr_w )  =/= write_ptr(ptr_w )) && (read_ptr(ptr_w - 1 , 0) === write_ptr(ptr_w - 1 , 0))).asUInt 
    empty_temp := (read_ptr === write_ptr).asUInt
    wcnt_flag  := !full_temp && io.write_en.asBool 
    when (wcnt_flag){
        write_ptr := write_ptr + 1.U
        fifo_buffer(write_ptr(ptr_w - 1 , 0)) := io.data_i   //data_temp_d_d
        exist_flag                  := exist_flag | ( 1.U << write_ptr(ptr_w - 1 , 0))
    }

    rcnt_flag  := !empty_temp && io.read_en.asBool 
    /******************************************************************************************************************************/
    // read/write pointer increment signal 
    // clear and write exist_flag bits
    when (rcnt_flag){
        read_ptr  := read_ptr + 1.U 
        exist_flag                := exist_flag & (~( 1.U << read_ptr(ptr_w - 1 , 0)))
    }
    when(wcnt_flag && rcnt_flag){
        exist_flag         := (exist_flag & (~( 1.U << read_ptr(ptr_w - 1 , 0)))) | ( 1.U << write_ptr(ptr_w - 1 , 0))
    }
    /******************************************************************************************************************************/
    io.full  := full_temp
    io.empty := empty_temp
    io.data_o := fifo_buffer(read_ptr(ptr_w - 1 , 0))
}