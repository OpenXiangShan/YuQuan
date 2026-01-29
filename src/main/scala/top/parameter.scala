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
    //AXI parameter
    ID_WIDTH    :   Int = 4  ,   // ID width
    TOKEN_WIDTH :   Int = 7  ,    // AXI read token width
    ADDR_WIDTH  :   Int = 36 ,  // address 
    DATA_WIDTH  :   Int = 512,  // data wi    
    STRB_WIDTH  :   Int = 64 ,   // wstrb wi  
    PRI_WIDTH   :   Int = 1  ,
    //SplitCmdIO parameter
    RANK_WIDTH  :   Int = 1  ,
    BG_WIDTH    :   Int = 2  ,
    BANK_WIDTH  :   Int = 2  ,
    ROW_WIDTH   :   Int = 16 ,
    COL_WIDTH   :   Int = 10 ,
    //dfi parameter
    ABITS       :   Int = 18 , //dfi ROW_WIDTH
    BABITS      :   Int = 2  , //dfi BANK_WIDTH
    BGBITS      :   Int = 2  , //dfi BG_WIDTH
    COLBITS     :   Int = 10 , //dfi COL_WIDTH
    RKBITS      :   Int = 1  , //dfi RANK_WIDTH
    DATABITS    :   Int = 256, //dfi DATA_WIDTH
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
    ResetNWidth   : Int = 1  ,
    CsNWidth      : Int = 1  , 
    ActNWidth     : Int = 1  ,
    RasNWidth     : Int = 1  ,
    CasNWidth     : Int = 1  ,
    WeNWidth      : Int = 1  ,
    CidBITS       : Int = 1  ,
    PARAMETERWIDTH: Int = 5  ,
    McParamWidth  : Int = 32 ,
    tZQINTVL_Witdh: Int = 32 ,
    RLmax         : Int = 30 ,
    Wlmax         : Int = 30  


    


)
object BUNDLE_PARAM extends BUNDLE_PARAMETER


case class CONFIG_PARAMETER(
    MC_CLK                : Int = 600,//MC actual operating frequency
    WrSchedulerQueueDepth : Int = 32 ,//WrSchedulerQueueDepth
    RdSchedulerQueueDepth : Int = 16  //RdSchedulerQueueDepth
)
object CONFIGURABLE_PARAM extends CONFIG_PARAMETER
