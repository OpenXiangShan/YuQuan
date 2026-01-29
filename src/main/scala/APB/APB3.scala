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
package apb

import chisel3._ 
import chisel3.util._

trait APBParam {
    val APB_AW = 12
    val APB_DW = 32
    val XLEN = 32
}

class APBIO extends Bundle with APBParam {
    // clock and rstn
    val pclk = Input(Clock())
    val presetn = Input(Bool())
    // input
    val paddr = Input(UInt(APB_AW.W))
    val pwdata = Input(UInt(APB_DW.W))
    val pwrite = Input(Bool())
    val psel = Input(Bool())
    val penable = Input(Bool())
    // output
    val pready = Output(Bool())
    val prdata = Output(UInt(APB_DW.W))
    val pslverr = Output(Bool())
}
