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


class filter_cmdinterface (val ADDR_WIDTH : Int , val PRIORITY_WIDTH : Int ,  val TOKEN_WIDTH : Int  ) extends Bundle{
      
    val addr       = Output(UInt(ADDR_WIDTH.W))       
    val pri        = Output(UInt(PRIORITY_WIDTH.W))      
    val token      = Output(UInt(TOKEN_WIDTH.W))
                  
    
}

class filter_wdatainterface (val DATA_WIDTH : Int , val PRIORITY_WIDTH : Int    ) extends Bundle{  
    val data       = Output(UInt(DATA_WIDTH.W))       
}

class filter_rdatainterface (val DATA_WIDTH : Int , val PRIORITY_WIDTH : Int ,  val TOKEN_WIDTH : Int  ) extends Bundle{
      
    val data       = Output(UInt(DATA_WIDTH.W))           
    val token      = Output(UInt(TOKEN_WIDTH.W))              
    
}


