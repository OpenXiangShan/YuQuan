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
import BUNDLE_PARAM._

class FlowCtrl (n:Int) extends  Module{
    val     io  = IO(new Bundle {
            val act_req         =  Flipped(Bool())
            val act_chosen      =  Flipped(UInt(log2Ceil(n).W))
            val cas_req         =  Flipped(Bool())
            val cas_chosen      =  Flipped(UInt(log2Ceil(n).W))
            val pre_req         =  Flipped(Bool())
            val pre_chosen      =  Flipped(UInt(log2Ceil(n).W))
            val act_won         =  Bool()
            val act_chosen_won  =  UInt(log2Ceil(n).W)
            val cas_won         =  Bool()
            val cas_chosen_won  =  UInt(log2Ceil(n).W)
            val pre_won         =  Bool()
            val pre_chosen_won  =  UInt(log2Ceil(n).W)     
    })
        //priority : act > cas > precharge
        when(io.act_req){
            io.act_won          := true.B
            io.act_chosen_won   := io.act_chosen
            io.cas_won          := false.B
            io.cas_chosen_won   := 0.U
            io.pre_won          := false.B
            io.pre_chosen_won   := 0.U
        }.elsewhen(io.cas_req){
            io.act_won   := false.B
            io.act_chosen_won   := 0.U
            io.cas_won   := true.B
            io.cas_chosen_won   := io.cas_chosen
            io.pre_won   := false.B
            io.pre_chosen_won   := 0.U
        }.elsewhen(io.pre_req){
            io.act_won   := false.B
            io.act_chosen_won   := 0.U
            io.cas_won   := false.B
            io.cas_chosen_won   := 0.U
            io.pre_won   := true.B
            io.pre_chosen_won   := io.pre_chosen 
        }.otherwise{
            io.act_won   := false.B
            io.act_chosen_won := 0.U
            io.cas_won   := false.B
            io.cas_chosen_won := 0.U
            io.pre_won   := false.B 
            io.pre_chosen_won := 0.U
        }
        
}