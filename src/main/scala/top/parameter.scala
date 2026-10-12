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
import java.util.ResourceBundle



/**********BUNDLE_PARAMETER**********/
case class BUNDLE_PARAMETER(
    TOKEN_WIDTH :   Int = 7,   //{overflow bit,counter bits} AXI token width
    ADDR_WIDTH  :   Int = 36 ,  // address 
    DATA_WIDTH  :   Int = 512,  // *data wi    
    STRB_WIDTH  :   Int = 64,   // wstrb wi  
    PRI_WIDTH   :   Int = 1,

//SplitCmdIO parameter
    RANK_WIDTH  :   Int = 1 ,
    BG_WIDTH    :   Int = 2 ,
    BANK_WIDTH  :   Int = 2 ,
    ROW_WIDTH   :   Int = 16,//取决于颗粒ROW地址位数
    COL_WIDTH   :   Int = 10,

    ABITS       :   Int = 18 , // dfi ROW_WIDTH
    BABITS      :   Int = 2  , //BANK_WIDTH
    BGBITS      :   Int = 2  , //BG_WIDTH
    COLBITS     :   Int = 10 , //COL_WIDTH
    RKBITS      :   Int = 1  , //RANK_WIDTH
    DATABITS    :   Int = 256, //DATA_WIDTH
    STRBBITS     :   Int = 32 , //STRB_WIDTH
    CKEBITS     :   Int = 1  ,
    ODTBITS     :   Int = 1  ,
    TOKENBITS   :   Int = 14 , //scg token width
    RdDataEnWidth : Int = 16 , 
    RdDataCsNWidth: Int = 32 ,
    RdDataVldWidth: Int = 16 , 
    RdDataDbiWidth: Int = 32 ,
    WrDataEnWidth : Int = 16 , 
    WrDataCsNWidth: Int = 32 ,
    CMDBITS       : Int = 3  ,
    

    ResetNWidth   : Int = 1    ,
    CsNWidth      : Int = 1    , 
    ActNWidth     : Int = 1    ,
    RasNWidth     : Int = 1    ,
    CasNWidth     : Int = 1    ,
    WeNWidth      : Int = 1    ,
    CidBITS       : Int = 1    ,
    PARAMETERWIDTH: Int = 5    ,

    McParamWidth  : Int = 32   ,
    TXN_FIFO_DEPTH: Int = 16   ,
    CMD_FIFO_DEPTH: Int = 16   ,

    tZQINTVL_Witdh: Int = 32   ,

    WrSchedulerQueueDepth : Int = 32 ,
    RdSchedulerQueueDepth : Int = 16 ,

  //dfi rddata parameter 
    RLmax         : Int =  10  ,
    trddata_en    : Int =  2   ,
    tphy_rdcslat  : Int =  2   ,
    tphy_rdlat    : Int =  4   ,
    Wlmax         : Int =  20  ,

    BANK_NUM       : Int  = 4  ,
    MC_CLK         : Int  = 600 //MHZ


)
object BUNDLE_PARAM extends BUNDLE_PARAMETER


case class ROB_PARAMETER(
  RobEntryNum  : Int = 128 ,
  VirtualChannelNum : Int = 1  
)
object  ROB_PARAM extends ROB_PARAMETER