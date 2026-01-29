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


/******************************************************************** IO for test*******************************************************************/
class TEST_IO extends Bundle{
    val test = Output(UInt(32.W))
}
/******************************************************************** AXI TOP_IO *******************************************************************/
class AXI_SYSIO extends Bundle{
    //AXI system
    val aclk    = Input(Bool())                             // AXI clock
    val arstn   = Input(Bool())                             // AXI asynchronous reset
}

class AXI_AWIO extends Bundle{
    //AXI write address
    val awid    = Input(UInt(AXI_PARAM.AXI_IDW.W))                    // AXI write address ID
    val awaddr  = Input(UInt(AXI_PARAM.AXI_ADDRW.W))                  // AXI write address          
    val awlen   = Input(UInt(AXI_PARAM.AXI_LENW.W))                   // AXI write burst length
    val awsize  = Input(UInt(AXI_PARAM.AXI_SIZEW.W))                  // AXI write burst size
    val awburst = Input(UInt(AXI_PARAM.AXI_BURSTW.W))                 // AXI write burst type
    val awuser  = Input(UInt(AXI_PARAM.AXI_USERW.W))                  // AXI write address User-defined signals (no used)
    val awqos   = Input(UInt(AXI_PARAM.AXI_QOSW.W))                   // AXI write address qos
    val awvalid = Input(Bool())                             // AXI write address valid
    val awready = Output(Bool())                            // AXI write address ready
}

class AXI_WIO extends Bundle{
    //AXI write data
    val wuser   = Input(UInt(AXI_PARAM.AXI_USERW.W))                  // AXI write data User-defined signals (no used)
    val wdata   = Input(UInt(AXI_PARAM.AXI_DATAW.W))                  // AXI write data
    val wstrb   = Input(UInt(AXI_PARAM.AXI_STRBW.W))                 // AXI write strobes
    val wlast   = Input(Bool())                             // AXI write last
    val wvalid  = Input(Bool())                             // AXI write valid
    val wready  = Output(Bool())                            // AXI write ready
}

class AXI_BIO extends Bundle{
    //AXI write response    
    val bid     = Output(UInt(AXI_PARAM.AXI_IDW.W))                   // AXI write response ID
    val bresp   = Output(UInt(AXI_PARAM.AXI_RESPW.W))                 // AXI write response
    val buser   = Output(UInt(AXI_PARAM.AXI_USERW.W))                 // AXI write response User-defined signals (no used)
    val bvalid  = Output(Bool())                            // AXI write response valid
    val bready  = Input(Bool())                             // AXI write response ready 
} 

class AXI_ARIO extends Bundle{
    //AXI read address  
    val arid    = Input(UInt(AXI_PARAM.AXI_IDW.W))                    // AXI read address ID
    val araddr  = Input(UInt(AXI_PARAM.AXI_ADDRW.W))                  // AXI read address
    val arlen   = Input(UInt(AXI_PARAM.AXI_LENW.W))                   // AXI read burst length
    val arsize  = Input(UInt(AXI_PARAM.AXI_SIZEW.W))                  // AXI read burst size
    val arburst = Input(UInt(AXI_PARAM.AXI_BURSTW.W))                 // AXI read burst type
    val aruser  = Input(UInt(AXI_PARAM.AXI_USERW.W))                  // AXI write address User-defined signals (no used)
    val arqos   = Input(UInt(AXI_PARAM.AXI_QOSW.W))                   // AXI read address qos          
    val arvalid = Input(Bool())                             // AXI read address valid 
    val arready = Output(Bool())                            // AXI read address ready
}

class AXI_RIO extends Bundle{
    //AXI read data
    val rid     = Output(UInt(AXI_PARAM.AXI_IDW.W))                   // AXI read ID                                            
    val ruser   = Output(UInt(AXI_PARAM.AXI_USERW.W))                 // AXI read data User-defined signals (no used)                                             
    val rdata   = Output(UInt(AXI_PARAM.AXI_DATAW.W))                 // AXI read data                                                                                   
    val rresp   = Output(UInt(AXI_PARAM.AXI_RESPW.W))                 // AXI read response                                            
    val rlast   = Output(Bool())                            // AXI read last                                    
    val rvalid  = Output(Bool())                            // AXI read valid                                    
    val rready  = Input(Bool())                             // AXI read ready 
}


/******************************************************************** axi_token IO *******************************************************************/
class TOKEN_IO extends Bundle{
    val artoken         = Output(UInt(TOKEN_PARAM.TOKEN_WIDTH.W))
}
class TOKEN_COUNTIO extends Bundle{
    val token_awen    =   Input(Bool()) 
    val token_awready =   Input(Bool()) 
    //val token_wvalid  =   Input(Bool())
    val token_aren    =   Input(Bool())
    val token_arready =   Input(Bool()) 
}

/******************************************************************** FIFO_IO *******************************************************************/
class FIFO_WIO(
    FIFO_DATAW  :   Int
) extends Bundle{
    val wen     = Input(Bool())
    val wdata   = Input(UInt(FIFO_DATAW.W))
    val full    = Output(Bool())
} 
class FIFO_RIO(
    FIFO_DATAW  :   Int
) extends Bundle{
    val ren     = Input(Bool())
    val rdata   = Output(UInt(FIFO_DATAW.W))
    val empty   = Output(Bool())
} 
class FIFO_EXIO(
    FIFO_DATAW  :   Int
) extends Bundle{     //FIFO extand IO
    val valid_margin    = Output(UInt(FIFO_DATAW.W))
} 
/******************************************************************** Read Reorder Buffer IO *******************************************************************/
class RROB_DATAIO extends Bundle{
    val rdata   = Output(UInt(UI_PARAM.UI_DATAW.W)) 
    val rvalid  = Output(Bool())
    val rready  = Input(Bool())
}

/******************************************************************** Consistency IO *******************************************************************/
class AXI_CMDIO extends Bundle{
    //AXI write address
    val aaddr  = Input(UInt(AXI_PARAM.AXI_ADDRW.W))                  // AXI write address          
    val alen   = Input(UInt(AXI_PARAM.AXI_LENW.W))                   // AXI write burst length
    val asize  = Input(UInt(AXI_PARAM.AXI_SIZEW.W))                  // AXI write burst size
    val aburst = Input(UInt(AXI_PARAM.AXI_BURSTW.W))                 // AXI write burst type
    val auser  = Input(UInt(AXI_PARAM.AXI_USERW.W))                  // AXI write address User-defined signals (no used)
    val aqos   = Input(UInt(AXI_PARAM.AXI_QOSW.W))                   // AXI write address qos
    val avalid = Input(Bool())                             // AXI write address valid
    val aready = Input(Bool())                            // AXI write address ready
}

class CONSIS_IO extends Bundle{
    val wconsis =   Output(Bool())
    val rconsis =   Output(Bool()) 
}

class CONS_ADDR_IO extends Bundle{
    val PTR_WIDTH   =   log2Ceil(AXI2UI_PARAM.AXIWFIFO_PARAMETER.FIFOAWL1_PARAMETER.FIFO_DEPTH + AXI2UI_PARAM.AXIWFIFO_PARAMETER.FIFOAWL2_PARAMETER.FIFO_DEPTH + 2)
    val TABLE_DEPTH =   1 << PTR_WIDTH

    val addr_start  =   Output(Vec(TABLE_DEPTH+1, UInt((AXI_PARAM.AXI_ADDRW*2).W)))
    val addr_end    =   Output(Vec(TABLE_DEPTH+1, UInt((AXI_PARAM.AXI_ADDRW*2).W)))
    val axi_ptr     =   Output(UInt(PTR_WIDTH.W))
    val ui_ptr      =   Output(UInt(PTR_WIDTH.W))    
}