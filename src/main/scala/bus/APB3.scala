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
package bus.apb3

import chisel3._ 
import chisel3.util._

object APBParameters {
    val addrBits = 12
    val dataBits = 32
    val regBits = 32
}

class APB3 extends Bundle {
    // clock and rstn
    val pclk = Input(Clock())
    val presetn = Input(Bool())
    // input
    val paddr = Input(UInt(APBParameters.addrBits.W))
    val pwdata = Input(UInt(APBParameters.dataBits.W))
    val pwrite = Input(Bool())
    val psel = Input(Bool())
    val penable = Input(Bool())
    // output
    val pready = Output(Bool())
    val prdata = Output(UInt(APBParameters.dataBits.W))
    val pslverr = Output(Bool())
}
