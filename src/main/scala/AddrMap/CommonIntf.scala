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

object CommonConst {
  val BankLen     = 2
  val GroupLen    = 2
  val RankLen     = 2
  val ColLen      = 10
  val RowLen      = 18
  val PriorityLen = 2
  val DataLen     = 512
}

// case class CommonParam(tokenLen: Int = 9, dataLen:Int = 512)

class DataBundle(tokenLen: Int = 10, dataLen: Int = CommonConst.DataLen) extends Bundle {
  val data  = UInt(dataLen.W)
  val token = UInt(tokenLen.W)
}

class AddrBundle(tokenLen: Int = 9) extends Bundle {
  val bank     = UInt(CommonConst.BankLen.W)
  val col      = UInt(CommonConst.ColLen.W)
  val group    = UInt(CommonConst.GroupLen.W)
  val rank     = UInt(CommonConst.RankLen.W)
  val row      = UInt(CommonConst.RowLen.W)
  val token    = UInt(tokenLen.W)
  val priority = Bool()
}
class WrCmdBundle(tokenLen: Int = 9, dataLen: Int = CommonConst.DataLen) extends AddrBundle(tokenLen) {
  val data = UInt(dataLen.W)
}
class RdCmdBundle(tokenLen: Int = 9) extends AddrBundle(tokenLen)

// //Read/Write CMD
class CmdBundle(tokenLen: Int = 9, dataLen: Int = CommonConst.DataLen) extends AddrBundle(tokenLen) {
  val cmd  = Bool()
  val data = UInt(dataLen.W)
}
