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
//different split rules
trait cmd_split_params {
  def bypass_cache = 0.U
  def bypass_scheduler = 1.U
  def split = 2.U
  def default = 3.U
}

class filter_cmd_split (val ADDR_WIDTH : Int , val PRIORITY_WIDTH : Int , val TOKEN_WIDTH : Int  ) extends Module with cmd_split_params{
    val io = IO(new Bundle{ 
        val mode                    = Input(UInt(2.W))
        val cmd_en                  = Input(Bool())
        val addr_boundary           = Input(UInt((ADDR_WIDTH*2).W))
           
        val cmd               = Input(UInt((ADDR_WIDTH).W))

        val cmd_valid         = Output(Bool())
        val cache_en          = Output(Bool())
    }
    )
    io.cmd_valid := io.cmd_en && io.mode =/= default
    io.cache_en := io.cmd_en & MuxLookup(io.mode, false.B)(Seq(
        bypass_scheduler -> true.B,
        split -> (io.cmd <= io.addr_boundary( ADDR_WIDTH*2 - 1 , ADDR_WIDTH)  && (io.cmd >= io.addr_boundary( ADDR_WIDTH - 1 , 0) ))
    ))
    // io.cache_en := true.B
}
