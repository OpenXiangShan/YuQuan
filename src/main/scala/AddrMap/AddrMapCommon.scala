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

trait OSMCParameter {
    // General Parameter for OSMC
    val ADDR_WIDTH = 32
    val PRI_WIDTH = 1
    val TOKEN_WIDTH = 10
    val LEN_WIDTH = 3
    val RANK_WIDTH = 1
    val BG_WIDTH = 2
    val BANK_WIDTH = 2
    val ROW_WIDTH = 16
    val COL_WIDTH =  10
    val RANKS = 2
    val DATA_WIDTH = 512
    val STRAB_WIDTH = 64
}

class CmdChannelIO extends Bundle with OSMCParameter{
    val addr = Output(UInt(ADDR_WIDTH.W))
    val pri = Output(UInt(PRI_WIDTH.W))
    val token = Output(UInt(TOKEN_WIDTH.W))
    val length = Output(UInt(LEN_WIDTH.W))
}

class RdDataChannelIO extends Bundle with OSMCParameter{
    val rdata = Output(UInt(DATA_WIDTH.W))
    val rtoken = Output(UInt(TOKEN_WIDTH.W))
    val rend = Output(Bool())   // reserved
}

class WrDataChannelIO extends Bundle with OSMCParameter{
    val wdata = Output(UInt(DATA_WIDTH.W))
    val wtoken = Output(UInt(TOKEN_WIDTH.W))
    val wstrab = Output(UInt(STRAB_WIDTH.W))
    val wend = Output(Bool())   // reserved
}
