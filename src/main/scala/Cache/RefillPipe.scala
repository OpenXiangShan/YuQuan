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
import utils._

sealed class RefillPipeIO extends CacheBundle {
    // Interface With MSHR
    val mshrRefillReq = Flipped(Decoupled(new MSHRInfo{ val rdata = UInt(BUNDLE_PARAM.DATA_WIDTH.W)}))

    // Write CacheArray
    val dataWriteBus = new CacheDataArrayWriteBus
    val tagWriteBus = new CacheTagArrayWriteBus

    // Interface With AddrMap
    val rdData2AddrMap = Decoupled(new RdDataIO)
}

class RefillPipe_by extends CacheModule {
    val io = FlatIO(new RefillPipeIO)

    val s1_valid, s2_valid = RegInit(false.B)
    val s1_ready, s2_ready = Wire(Bool())

    // stage1
    s1_ready := (!s1_valid | s2_ready)
    val s1_bits = RegEnable(io.mshrRefillReq.bits, 0.U.asTypeOf(io.mshrRefillReq.bits), io.mshrRefillReq.fire)
    when(io.mshrRefillReq.fire) {
        s1_valid := true.B
    }.elsewhen(s1_valid && s2_ready) {
        s1_valid := false.B
    }

    // stage2
    val IsFinish = Wire(Bool())
    s2_ready := (!s2_valid | IsFinish) && io.dataWriteBus.req.ready && io.tagWriteBus.req.ready
    val s2_bits = RegEnable(s1_bits, 0.U.asTypeOf(s1_bits), s1_valid && s2_ready)
    val addr = getAddr(s2_bits.splitCmd).asTypeOf(addrBundle)
    val cacheArrayFire = io.dataWriteBus.req.fire && io.tagWriteBus.req.fire
    IsFinish := Mux(io.mshrRefillReq.bits.prefetch, cacheArrayFire, cacheArrayFire && io.rdData2AddrMap.fire)
    when(s1_valid && s2_ready) {
        s2_valid := true.B
    }.elsewhen(IsFinish) {
        s2_valid := false.B
    }

    io.dataWriteBus.apply(valid = s2_valid, wdata = s2_bits.rdata, bankIdx = addr.bank, setIdx = addr.set, waymask = s2_bits.waymask)
    io.tagWriteBus.apply(valid = s2_valid, wdata = Cat(addr.tag, true.B, false.B, true.B).asTypeOf(new TagDataBundle), bankIdx = addr.bank, setIdx = addr.set, waymask = s2_bits.waymask)
    io.mshrRefillReq.ready := s1_ready
    io.rdData2AddrMap.valid := s2_valid && (!s2_bits.prefetch)
    io.rdData2AddrMap.bits := Cat(s2_bits.rdata, s2_bits.splitCmd.token).asTypeOf(new RdDataIO)
}
