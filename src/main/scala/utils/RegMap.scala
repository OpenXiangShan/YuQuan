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
package utils

import chisel3._ 
import chisel3.util._

object MaskedRegMap {
    def FullMask = Fill(32, true.B)
    def EmptyMap = 0.U(32.W)
    // reg attribute
    def WritableMask = FullMask
    def UnwritableMask = EmptyMap
    def DefaultStaticMask = FullMask
    def DefaultQuasiDynamicMask = EmptyMap
    def apply(addr: Int, reg: UInt, wmask: UInt = WritableMask, smask: UInt = DefaultStaticMask, qmask: UInt = DefaultQuasiDynamicMask)
        = (addr, (reg, wmask, smask, qmask))
    def generate(mapping: Map[Int, (UInt, UInt, UInt, UInt)], raddr: UInt, rdata: UInt, waddr: UInt, wen: Bool, wdata: UInt, sen: Bool, qen: Bool): Unit = {
        val chiselMapping = mapping.map { case (a, (r, wm, sm, qm)) => (a.U, r, wm, sm, qm) }
        rdata := MuxLookup(raddr, 0.U)(chiselMapping.map { case (a, r, wm, sm, qm) => (a, r) }.toSeq)
        chiselMapping.map { case (a, r, wm, sm, qm) => 
            if (wm != UnwritableMask) when (wen && waddr === a) { r := MaskData(r, wdata, wm & (sm & Fill(32, sen) | qm & Fill(32, qen) | ~sm & ~qm)) } }
    }
    def isIllegalAddr(mapping: Map[Int, (UInt, UInt, UInt, UInt)], addr: UInt): Bool = {
        val illegalAddr = Wire(Bool())
        val chiselMapping = mapping.map { case (a, (r, wm, sm, qm)) => (a.U, r, wm, sm, qm) }
        illegalAddr := MuxLookup(addr, true.B)(chiselMapping.map { case (a, r, wm, sm, qm) => (a, false.B) }.toSeq)
        illegalAddr
    }
    def isDynamicReg(mapping: Map[Int, (UInt, UInt, UInt, UInt)], addr: UInt): Bool = {
        val dynamicReg = Wire(Bool())
        val chiselMapping = mapping.map { case (a, (r, wm, sm, qm)) => (a.U, r, wm, sm, qm) }
        dynamicReg := MuxLookup(addr, false.B)(chiselMapping.map { case (a, r, wm, sm, qm) => (a, ((sm | qm) =/= FullMask).asBool) }.toSeq)
        dynamicReg
    }
    def generate(mapping: Map[Int, (UInt, UInt, UInt, UInt)], addr: UInt, rdata: UInt, wen: Bool, wdata: UInt, sen: Bool, qen: Bool): Unit = 
        generate(mapping, addr, rdata, addr, wen, wdata, sen, qen)
}
