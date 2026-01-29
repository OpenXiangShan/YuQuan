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

class SRAMBundleA(val set: Int) extends Bundle {
  val setIdx = Output(UInt(log2Up(set).W))

  def apply(setIdx: UInt) = {
    this.setIdx := setIdx
    this
  }
}

class SRAMBundleR[T <: Data](private val gen: T, val way: Int = 1) extends Bundle {
  val data = Output(Vec(way, gen))
}

class SRAMReadBus[T <: Data](private val gen: T, val set: Int, val way: Int = 1) extends Bundle {
  val req = Decoupled(new SRAMBundleA(set))
  val resp = Flipped(new SRAMBundleR(gen, way))

  def apply(valid: Bool, setIdx: UInt) = {
    this.req.bits.apply(setIdx)
    this.req.valid := valid
    this
  }
}

class WCBReqBundle extends CacheBundle {
  val data = UInt(CacheLineBits.W)
  val tag = UInt(TagBits.W)
  val set = UInt(BankSetBits.W)
  val bank = UInt(BankBits.W)
}

class MSHRInfo extends CacheBundle{
  val splitCmd = new SplitCmdIO
  val waymask = UInt(Ways.W)
  val prefetch = Bool()
}

trait CacheConst {
    // Config
    val TotalSize = 256  // kB
    val PAddrBits = BUNDLE_PARAM.ADDR_WIDTH
    val LineSize = 64   // Byte
    val Ways = 4
    val Banks = 8
    val nMissEntries = 64

    val LineBeats = LineSize / 8    // Data Width 64bits
    val Sets = TotalSize * 1024 /*  * 1024 *// LineSize / Ways 
    val BankSets = TotalSize * 1024 /* * 1024 */ / LineSize / Ways / Banks
    val OffsetBits = log2Up(LineSize)
    val SetBits = log2Up(Sets)
    val BankBits = log2Up(Banks)
    val BankSetBits = log2Up(BankSets)
    val WordIndexBits = log2Up(LineBeats)
    val TagBits = PAddrBits - OffsetBits - SetBits
    val CacheLineBits = LineSize * 8

    val COL_WIDTH = BUNDLE_PARAM.COL_WIDTH
    val cTokenLen = BUNDLE_PARAM.TOKEN_WIDTH + log2Up(nMissEntries)
    def addrBundle = new Bundle {
        val tag = UInt(TagBits.W)
        //val index = UInt(SetBits.W) // All Banks
        val set = UInt(BankSetBits.W)
        val bank = UInt(BankBits.W)
        val wordIndex = UInt(WordIndexBits.W)
        val byteOffset = UInt((if (LineSize == 64) 3 else 2).W)
    }
    def getAddr(cmd: SplitCmdIO): UInt = {
      Cat(cmd.rank, cmd.row, cmd.col(COL_WIDTH-1,3), 
                      cmd.bank, cmd.bg, cmd.col(2,0)) << 3
    }
    def getSetIdex(addr: UInt) = addr.asTypeOf(addrBundle).set
    def getBankIdex(addr: UInt) = addr.asTypeOf(addrBundle).bank
}

