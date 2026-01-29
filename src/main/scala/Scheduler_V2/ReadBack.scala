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

case class RbParam(bufferSize: Int = 4, cTokenLen: Int = 16, fTokenLen: Int = 10)

class DataIO(tokenLen: Int) extends Bundle with OSMCParameter {
  val token = UInt(tokenLen.W)
  val data  = UInt(DATA_WIDTH.W)
}

class ReadBackIO(p: RbParam) extends Bundle {
  val mTokenLen   = math.max(p.cTokenLen, p.fTokenLen) + 1
  val dataFromScg = Flipped(Decoupled(new DataBundle(tokenLen = mTokenLen)))
  val data2Cache  = Decoupled(new DataBundle(tokenLen = p.cTokenLen))
  val data2Filter = Decoupled(new DataBundle(tokenLen = p.fTokenLen))
}
class ReadBack(p: RbParam) extends Module {
  val mTokenLen = math.max(p.cTokenLen, p.fTokenLen) + 1
  val io        = IO(new ReadBackIO(new RbParam()))
  val bufferSize = p.bufferSize

  val mem = Mem(
    bufferSize,
    chiselTypeOf(io.dataFromScg.bits)
  )
  val rdPtr = RegInit(0.U(log2Ceil(bufferSize).W))
  val wrPtr = RegInit(0.U(log2Ceil(bufferSize).W))
  val count = RegInit(0.U(log2Ceil(bufferSize + 1).W))

  val full  = count === bufferSize.U
  val empty = count === 0.U
  io.dataFromScg.ready := ~full

  // receive(enq)
  when(io.dataFromScg.fire) {
    mem(wrPtr) := io.dataFromScg.bits
    wrPtr      := wrPtr + 1.U
    count      := count + 1.U
  }
  // resp direction based on token's highest bit
  val head    = mem(rdPtr)
  val toCache = Reverse(head.token)(0)
  io.data2Cache.valid  := (~empty & toCache)
  io.data2Filter.valid := (~empty & !toCache)
  io.data2Cache.bits   := Mux(io.data2Cache.valid, head, 0.U.asTypeOf(head))
  io.data2Filter.bits  := Mux(io.data2Filter.valid, head, 0.U.asTypeOf(head))

  // sendback(deq)
  val deq = io.data2Cache.fire | io.data2Filter.fire
  when(deq) {
    rdPtr := rdPtr + 1.U
    count := count - 1.U
  }

  when(deq & io.dataFromScg.fire) { // both deq and enq fire, count don't change
    count := count
  }
}
