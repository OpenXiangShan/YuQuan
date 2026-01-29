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


/**********AXI_PARAMETER**********/
case class AXI_PARAMETER(
    AXI_ADDRW   :  Int = 36 ,                               // AXI address 
    AXI_DATAW   :  Int = 256,                               // AXI *data wi    
    AXI_STRBW   :  Int = 32/*AXI_PARAM.AXI_DATAW / 8*/,     // AXI wstrb wi
    AXI_IDW     :  Int = 14  ,                               // AXI ID width
    AXI_LENW    :  Int = 8  ,                               // AXI a*len wi
    AXI_SIZEW   :  Int = 3/*log2Floor(AXI_PARAM.AXI_DATAW/8)*/,  // AXI a*size w
    AXI_BURSTW  :  Int = 2  ,                               // AXI a*burst 
    AXI_LOCKW   :  Int = 2  ,                               // AXI a*lock w
    AXI_USERW   :  Int = 1  ,           
    AXI_CACHEW  :  Int = 4  ,                               // AXI a*cache 
    AXI_PROTW   :  Int = 3  ,                               // AXI a*prot w
    AXI_QOSW    :  Int = 4  ,                               // AXI a*qos wi
    AXI_RESPW   :  Int = 2  ,                               // AXI *resp wi
    OCPAR_ADDR_PAR_WIDTH    :   Int = 8 // AXI awparity w 
)
object AXI_PARAM extends AXI_PARAMETER

/**********UI_PARAMETER**********/
case class UI_PARAMETER(
    UI_ADDRW    :  Int = 36 ,         // AXI address 
    UI_DATAW    :  Int = 512,         // AXI *data wi    
    UI_STRBW    :  Int = 64,         // AXI wstrb wi    
)
object UI_PARAM extends UI_PARAMETER

/**********FIFO_PARAMETER**********/
case class FIFO_PARAMETER(
    FIFO_WIDTH   :   Int = 256,//128/*AXI_PARAM.AXI_ADDRW + AXI_PARAM.AXI_LENW +  + AXI_PARAM.AXI_BURSTWAXI_PARAM.AXI_IDW + AXI_PARAM.AXI_USERW + 1 + AXI_PARAM.AXI_QOSW + AXI_PARAM.AXI_SIZEW*/,  //AW FIFO WIDTH 
    FIFO_DEPTH   :   Int = 8,
    FIFO_MAGRINW :   Int = 3/*log2Floor(FIFOAW_PARAM.FIFO_DEPTH_AW)*/
)
//object FIFO_PARAM extends FIFO_PARAMETER

/**********TOKEN_PARAMETER**********/
case class TOKEN_PARAMETER(
    TOKEN_WIDTH :   Int = 7,   //{overflow bit,counter bits}
    //TOKEN_FAST_BUFFER_DEPTH :   Int = 128   //filter buffer
)
object TOKEN_PARAM extends TOKEN_PARAMETER

/********************************************************************************************************************************************************************/

class AXI2UI_PARAMETER{
    val AXI_PARAMETER       = new AXI_PARAMETER()
    val UI_PARAMETER        = new UI_PARAMETER()
    val AXIWFIFO_PARAMETER  = new AXIWFIFO_PARAMETER()
    val AXIRFIFO_PARAMETER  = new AXIRFIFO_PARAMETER()
    val TOKEN_PARAMETER     = new TOKEN_PARAMETER()
}
object AXI2UI_PARAM extends AXI2UI_PARAMETER

class AXIWFIFO_PARAMETER{
    val FIFOAWL1_PARAMETER  = new FIFO_PARAMETER(AXI_PARAM.AXI_ADDRW + AXI_PARAM.AXI_LENW +  + AXI_PARAM.AXI_BURSTW + /*AXI_PARAM.AXI_IDW + AXI_PARAM.AXI_USERW*/ + 1 + AXI_PARAM.AXI_QOSW + AXI_PARAM.AXI_SIZEW, 16, log2Floor(16))
    val FIFOAWL2_PARAMETER  = new FIFO_PARAMETER(AXI_PARAM.AXI_ADDRW + TOKEN_PARAM.TOKEN_WIDTH, 8, log2Floor(8))
    val FIFOWL1_PARAMETER   = new FIFO_PARAMETER(AXI_PARAM.AXI_DATAW + AXI_PARAM.AXI_STRBW + 1, 16, log2Floor(16))    //1=AXI_LASTW
    val FIFOWL2_PARAMETER   = new FIFO_PARAMETER(UI_PARAM.UI_DATAW + UI_PARAM.UI_STRBW/*+TOKEN_PARAM.TOKEN_WIDTH*/, 16, log2Floor(16))    //1=AXI_LASTW
    val FIFOB_PARAMETER     = new FIFO_PARAMETER(AXI_PARAM.AXI_IDW + AXI_PARAM.AXI_USERW, 16, log2Floor(16))    //16 = AW8+8
    val FIFOBO_PARAMETER    = new FIFO_PARAMETER(1, 16, log2Floor(16))
}

class AXIRFIFO_PARAMETER{
    val FIFOARL1_PARAMETER  = new FIFO_PARAMETER(AXI_PARAM.AXI_ADDRW + AXI_PARAM.AXI_LENW +  + AXI_PARAM.AXI_BURSTW + /*AXI_PARAM.AXI_IDW + AXI_PARAM.AXI_USERW*/ + 1 + AXI_PARAM.AXI_QOSW + AXI_PARAM.AXI_SIZEW, 4, log2Floor(4))
    val FIFOARL2_PARAMETER  = new FIFO_PARAMETER(AXI_PARAM.AXI_ADDRW + TOKEN_PARAM.TOKEN_WIDTH, 8, log2Floor(8))
    val FIFORL1_PARAMETER   = new FIFO_PARAMETER(AXI_PARAM.AXI_DATAW + 1, 8, log2Floor(8))    //1=AXI_LASTW             //keep same with FIFOCMD_TOKEN  in Depth
    val FIFOCMD_PARAMETER   = new FIFO_PARAMETER(AXI_PARAM.AXI_LENW  + AXI_PARAM.AXI_SIZEW, 256, log2Floor(256))        //keep same with FIFOCMD        in Depth    
    val FIFOCMD_TOKEN_PARAMETER = new FIFO_PARAMETER(TOKEN_PARAM.TOKEN_WIDTH, 256, log2Floor(256))
    val RROB_ADDQ_PARAMETER = new FIFO_PARAMETER(7, 128, log2Floor(128))
}
