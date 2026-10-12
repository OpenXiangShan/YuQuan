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
package bus.axi4

import chisel3._
import chisel3.util._

object AXI4Parameters {
    // basic parameters
    val addrBits  = 64  // address width
    val dataBits  = 256 // data width
    val idBits    = 14  // number of bits to represent ID  Q: too broad?
    val lenBits   = 8   // number of bits to represent burst length
    val sizeBits  = 3   // log2Floor(dataBits/8)
    val strbBits  = dataBits / 8 // number of bits to represent strobe
    val respBits  = 2   // response type: OKAY, EXOKAY, SLVERR, DECERR
    // additional parameters
    val lockBits  = 2   // lock type: normal, exclusive, locked
    val burstBits = 2   // burst type: 00-fixed, 01-increment, 10-wrap
    val protBits  = 3   // protection type: normal, privileged, secure, data access, inst access, etc.
    val cacheBits = 4   // memory type: bufferable, cacheable, etc.
    val qosBits   = 4   // quality of service: priority level

    // AXI memory types
    def CACHE_BUFFERABLE    = 1.U(cacheBits.W)
    def CACHE_MODIFIABLE    = 2.U(cacheBits.W)
    def CACHE_READALLOCATE  = 4.U(cacheBits.W)
    def CACHE_WRITEALLOCATE = 8.U(cacheBits.W)

    // AXI response types
    def RESP_OKAY           = 0.U(respBits.W)
    def RESP_EXOKAY         = 1.U(respBits.W)
    def RESP_SLVERR         = 2.U(respBits.W)
    def RESP_DECERR         = 3.U(respBits.W)

    // AXI protection types
    def PROT_PRIVILEGED     = 1.U(protBits.W)
    def PROT_SECURE         = 2.U(protBits.W)
    def PROT_INSTRUCTION    = 4.U(protBits.W)

    // AXI burst types
    def BURST_FIXED         = 0.U(burstBits.W)
    def BURST_INCR          = 1.U(burstBits.W)
    def BURST_WRAP          = 2.U(burstBits.W)

    // AXI lock types
    def LOCK_NORMAL         = 0.U(lockBits.W)
    def LOCK_EXCLUSIVE      = 1.U(lockBits.W)
    def LOCK_LOCKED         = 2.U(lockBits.W)
}

class AXI4BundleA extends Bundle {
    val id     = UInt(AXI4Parameters.idBits.W)
    val addr   = UInt(AXI4Parameters.addrBits.W)
    val len    = UInt(AXI4Parameters.lenBits.W)
    val size   = UInt(AXI4Parameters.sizeBits.W)
    val burst  = UInt(AXI4Parameters.burstBits.W)
    val lock   = UInt(AXI4Parameters.lockBits.W)
    val cache  = UInt(AXI4Parameters.cacheBits.W)
    val prot   = UInt(AXI4Parameters.protBits.W)
    val qos    = UInt(AXI4Parameters.qosBits.W)
}

class AXI4BundleW extends Bundle {
    val data   = UInt(AXI4Parameters.dataBits.W)
    val strb   = UInt(AXI4Parameters.strbBits.W)
    val last   = Bool()
}

class AXI4BundleB extends Bundle {
    val id     = UInt(AXI4Parameters.idBits.W)
    val resp   = UInt(AXI4Parameters.respBits.W)
}

class AXI4BundleR extends Bundle {
    val id     = UInt(AXI4Parameters.idBits.W)
    val data   = UInt(AXI4Parameters.dataBits.W)
    val resp   = UInt(AXI4Parameters.respBits.W)
    val last   = Bool()
}

class AXI4 extends Bundle {
    // Write Address Channel
    val aw = Flipped(Decoupled(new AXI4BundleA))
    // Write Data Channel
    val w  = Flipped(Decoupled(new AXI4BundleW))
    // Write Response Channel
    val b  = Decoupled(new AXI4BundleB)
    // Read Address Channel
    val ar = Flipped(Decoupled(new AXI4BundleA))
    // Read Data Channel
    val r  = Decoupled(new AXI4BundleR)
}
